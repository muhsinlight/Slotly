package com.slotly.api.exception;

import com.slotly.api.response.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
        MethodArgumentNotValidException exception
    ) {
        Map<String, String> fieldErrors = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                (first, second) -> first
            ));

        return response(HttpStatus.BAD_REQUEST, "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return response(status, message, null);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabase(DataAccessException exception) {
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "A database operation failed",
            null
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "You do not have permission for this operation", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred",
            null
        );
    }

    private <T> ResponseEntity<ApiResponse<T>> response(
        HttpStatus status,
        String message,
        T data
    ) {
        ApiResponse<T> body = ApiResponse.<T>builder()
            .status(status)
            .message(message)
            .data(data)
            .build();
        return ResponseEntity.status(status).body(body);
    }
}

\r\n