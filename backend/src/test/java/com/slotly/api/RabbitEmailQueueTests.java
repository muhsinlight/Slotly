package com.slotly.api;

import com.slotly.api.module.notification.service.EmailDispatcher;
import com.slotly.api.module.notification.service.EmailDeliveryScheduler;
import com.slotly.api.module.notification.service.EmailGateway;
import com.slotly.api.module.notification.service.RabbitEmailQueue;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RabbitEmailQueueTests {
    @Test
    void publishesCommittedOutboxIdsAndConsumesById() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        EmailDispatcher dispatcher = mock(EmailDispatcher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        UUID messageId = UUID.randomUUID();
        when(dispatcher.canDeliver()).thenReturn(true);
        when(jdbc.queryForList(any(String.class), eq(UUID.class), any(Timestamp.class), any(Timestamp.class), any(Timestamp.class)))
            .thenReturn(List.of(messageId));

        RabbitEmailQueue queue = new RabbitEmailQueue(jdbc, clock, rabbit, dispatcher);
        queue.publishReady();
        queue.consume(messageId.toString());

        verify(rabbit).convertAndSend("slotly.email.ready", messageId.toString());
        verify(dispatcher).deliver(messageId);
    }

    @Test
    void bothTransportsRequireAnEnabledProvider() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        EmailGateway provider = mock(EmailGateway.class);
        var beans = new StaticListableBeanFactory();
        Clock clock = Clock.systemUTC();
        var gateways = beans.getBeanProvider(EmailGateway.class);
        var missingProvider = new EmailDispatcher(jdbc, clock, gateways, true);
        var id = UUID.randomUUID().toString();
        var queue = new RabbitEmailQueue(jdbc, clock, rabbit, missingProvider);
        queue.publishReady();
        queue.consume(id);

        beans.addBean("provider", provider);
        var disabled = new EmailDispatcher(jdbc, clock, gateways, false);
        var disabledQueue = new RabbitEmailQueue(jdbc, clock, rabbit, disabled);
        disabledQueue.publishReady();
        disabledQueue.consume(id);
        new EmailDeliveryScheduler(disabled).deliver();
        verifyNoInteractions(jdbc, rabbit, provider);
    }
}
