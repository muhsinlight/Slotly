package com.slotly.api.module.notification.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Configuration
@ConditionalOnProperty(name = "slotly.rabbitmq.enabled", havingValue = "true")
class RabbitEmailQueueConfiguration {
    static final String QUEUE = "slotly.email.ready";

    @Bean
    Queue emailReadyQueue() {
        return new Queue(QUEUE, true);
    }
}

@Component
@ConditionalOnProperty(name = "slotly.rabbitmq.enabled", havingValue = "true")
public class RabbitEmailQueue {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final RabbitTemplate rabbit;
    private final EmailDispatcher dispatcher;

    public RabbitEmailQueue(JdbcTemplate jdbc, Clock clock, RabbitTemplate rabbit, EmailDispatcher dispatcher) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.rabbit = rabbit;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 10_000)
    public void publishReady() {
        var ids = jdbc.queryForList("""
            SELECT id FROM email_outbox WHERE state = 'PENDING' AND next_attempt_at <= ?
            ORDER BY created_at LIMIT 100
            """, UUID.class, Timestamp.from(clock.instant()));
        for (UUID id : ids) {
            rabbit.convertAndSend(RabbitEmailQueueConfiguration.QUEUE, id.toString());
        }
    }

    @RabbitListener(queues = RabbitEmailQueueConfiguration.QUEUE)
    public void consume(String messageId) {
        dispatcher.deliver(UUID.fromString(messageId));
    }
}
