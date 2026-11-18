package com.slotly.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.slotly.api.common.aop.OwnedPlace;
import com.slotly.api.common.aop.PlaceOwnershipAspect;
import com.slotly.api.common.aop.ServiceLoggingAspect;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.user.entity.User;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceAspectTests {
    @Test
    void ownershipDeniesBeforeExecutingAndUsesRequestedLock() {
        PlaceRepository places = mock(PlaceRepository.class);
        UUID ownerId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        Place place = mock(Place.class);
        User owner = mock(User.class);
        when(place.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(ownerId);
        when(places.findLockedById(placeId)).thenReturn(Optional.of(place));

        ProtectedService target = new ProtectedService();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new PlaceOwnershipAspect(places));
        ProtectedService proxy = factory.getProxy();

        assertEquals("ok", proxy.change(ownerId, placeId));
        assertEquals(1, target.calls);
        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
            () -> proxy.change(UUID.randomUUID(), placeId));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals(1, target.calls);
        verify(places, times(2)).findLockedById(placeId);
        verify(places, never()).findById(any());

        when(places.findLockedById(placeId)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> proxy.change(ownerId, placeId)).getStatusCode());
        assertEquals(1, target.calls);
    }

    @Test
    void loggingPreservesResultAndExceptionWithoutLoggingPayload() throws Throwable {
        Logger logger = (Logger) LoggerFactory.getLogger(ServiceLoggingAspect.class);
        Level previous = logger.getLevel();
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            var invocation = mock(org.aspectj.lang.ProceedingJoinPoint.class);
            var signature = mock(org.aspectj.lang.Signature.class);
            when(invocation.getSignature()).thenReturn(signature);
            when(signature.toShortString()).thenReturn("AuthService.login(..)");
            when(invocation.proceed()).thenReturn("secret-token");
            ServiceLoggingAspect aspect = new ServiceLoggingAspect();
            assertEquals("secret-token", aspect.observe(invocation));
            IllegalArgumentException failure = new IllegalArgumentException("secret-password");
            when(invocation.proceed()).thenThrow(failure);
            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> aspect.observe(invocation)));
            assertEquals(2, appender.list.size());
            String logs = appender.list.toString();
            assertTrue(logs.contains("durationMs="));
            assertTrue(logs.contains("outcome=success"));
            assertTrue(logs.contains("outcome=IllegalArgumentException"));
            assertFalse(logs.contains("secret"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }
    }

    public static class ProtectedService {
        int calls;

        @OwnedPlace(lock = true)
        public String change(UUID ownerId, UUID placeId) {
            calls++;
            return "ok";
        }
    }
}
