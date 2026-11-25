package com.slotly.api;

import com.slotly.api.common.exception.ApiException;
import com.slotly.api.common.exception.BaseApiException;
import com.slotly.api.common.exception.DatabaseException;
import com.slotly.api.common.exception.GlobalExceptionHandler;
import com.slotly.api.common.exception.RabbitMqException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsApiExceptionToItsStatusMessageAndCode() {
        var response = handler.handleApiException(
                new ApiException(HttpStatus.CONFLICT, "The appointment conflicts with another booking")
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CONFLICT", response.getBody().getCode());
        assertEquals("The appointment conflicts with another booking", response.getBody().getMessage());
    }

    @Test
    void convertsDatabaseErrorsToSanitizedApiResponses() {
        var response = handler.handleDatabase(
                new DataAccessResourceFailureException("database password must not be exposed")
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DATABASE_ERROR", response.getBody().getCode());
        assertEquals("A database operation failed", response.getBody().getMessage());
        assertTrue(!response.getBody().getMessage().contains("password"));
    }

    @Test
    void convertsRabbitMqErrorsToSanitizedServiceUnavailableResponses() {
        var response = handler.handleRabbitMq(new AmqpException("broker credentials must not be exposed"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RABBITMQ_ERROR", response.getBody().getCode());
        assertEquals("The messaging service is temporarily unavailable", response.getBody().getMessage());
        assertTrue(!response.getBody().getMessage().contains("credentials"));
    }

    @Test
    void infrastructureExceptionsUseDistinctApiErrorTypes() {
        assertEquals(BaseApiException.class, ApiException.class.getSuperclass());
        assertEquals(BaseApiException.class, DatabaseException.class.getSuperclass());
        assertEquals(BaseApiException.class, RabbitMqException.class.getSuperclass());
        assertEquals("DATABASE_ERROR", new DatabaseException(new RuntimeException()).getCode());
        assertEquals("RABBITMQ_ERROR", new RabbitMqException(new RuntimeException()).getCode());
    }
}
