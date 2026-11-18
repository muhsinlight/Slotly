package com.slotly.api.common.aop;

import com.slotly.api.module.place.repository.PlaceRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Aspect
@Component
@Order(10)
public class PlaceOwnershipAspect {
    private final PlaceRepository places;

    public PlaceOwnershipAspect(PlaceRepository places) {
        this.places = places;
    }

    @Before("@annotation(policy)")
    public void authorize(JoinPoint invocation, OwnedPlace policy) {
        Object[] arguments = invocation.getArgs();
        UUID ownerId = (UUID) arguments[0];
        UUID placeId = (UUID) arguments[1];
        var place = (policy.lock() ? places.findLockedById(placeId) : places.findById(placeId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found"));
        if (!place.getOwner().getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Place belongs to another owner");
        }
    }
}
