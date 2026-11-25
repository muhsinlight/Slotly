package com.slotly.api.common.exception;

import com.slotly.api.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null
                                ? "Invalid value"
                                : error.getDefaultMessage(),
                        (first, second) -> first
                ));

        return response(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                fieldErrors,
                "VALIDATION_ERROR"
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(
            HttpMessageNotReadableException exception
    ) {
        return response(HttpStatus.BAD_REQUEST, "Invalid request body", null, "BAD_REQUEST");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, ServletRequestBindingException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestParameter(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "Invalid or missing request parameter", null, "BAD_REQUEST");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "Resource not found", null, "NOT_FOUND");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception
    ) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "Request method is not supported", null, "METHOD_NOT_ALLOWED");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception
    ) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", null, "UNSUPPORTED_MEDIA_TYPE");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception
    ) {
        return response(HttpStatus.NOT_ACCEPTABLE, "Requested media type is not acceptable", null, "NOT_ACCEPTABLE");
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        String message = exception.getReason() == null
                ? exception.getStatus().getReasonPhrase()
                : exception.getReason();
        return response(exception.getStatus(), message, null, exception.getCode());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabase(
            DataAccessException exception
    ) {
        return handleDatabaseException(new DatabaseException(exception));
    }

    @ExceptionHandler(DatabaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabaseException(
            DatabaseException exception
    ) {
        logFailure("Database operation failed", exception);
        return response(
                exception.getStatus(),
                exception.getReason(),
                null,
                exception.getCode()
        );
    }

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ApiResponse<Void>> handleRabbitMq(AmqpException exception) {
        return handleRabbitMqException(new RabbitMqException(exception));
    }

    @ExceptionHandler(RabbitMqException.class)
    public ResponseEntity<ApiResponse<Void>> handleRabbitMqException(
            RabbitMqException exception
    ) {
        logFailure("RabbitMQ operation failed", exception);
        return response(
                exception.getStatus(),
                exception.getReason(),
                null,
                exception.getCode()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException exception
    ) {
        return response(
                HttpStatus.FORBIDDEN,
                "You do not have permission for this operation",
                null,
                "FORBIDDEN"
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception exception
    ) {
        logFailure("Unexpected request failure", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                null,
                "INTERNAL_ERROR"
        );
    }

    private <T> ResponseEntity<ApiResponse<T>> response(
            HttpStatus status,
            String message,
            T data,
            String code
    ) {
        ApiResponse<T> body = ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .code(code)
                .data(data)
                .build();

        return ResponseEntity.status(status).body(body);
    }

    private void logFailure(String message, Exception exception) {
        String errorId = UUID.randomUUID().toString();
        logger.error("[{}] {} | details: slotly.log", errorId, message, exception);
    }

}
