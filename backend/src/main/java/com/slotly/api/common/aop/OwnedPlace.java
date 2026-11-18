package com.slotly.api.common.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** First two arguments must be ownerId and placeId (UUID). Requires a transaction. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OwnedPlace {
    boolean lock() default false;
}
