package com.slotly.api.common.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Order(-10)
public class ServiceLoggingAspect {
    private static final Logger logger = LoggerFactory.getLogger(ServiceLoggingAspect.class);

    @Around("execution(public * com.slotly.api.module..service..*(..)) && @within(org.springframework.stereotype.Service)")
    public Object observe(ProceedingJoinPoint invocation) throws Throwable {
        long start = System.nanoTime();
        String outcome = "success";
        try {
            return invocation.proceed();
        } catch (Throwable failure) {
            outcome = failure.getClass().getSimpleName();
            throw failure;
        } finally {
            logger.debug("service={} outcome={} durationMs={}",
                invocation.getSignature().toShortString(), outcome,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
    }
}
