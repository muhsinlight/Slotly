package com.slotly.api.response;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ApiResponse<T> {
    private final int status;
    private final String message;
    private final T data;
    private final Instant timestamp;

    private ApiResponse(Builder<T> builder) {
        this.status = builder.status.value();
        this.message = builder.message;
        this.data = builder.data;
        this.timestamp = builder.timestamp;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public static class Builder<T> {
        private String message;
        private T data;
        private Instant timestamp = Instant.now();
        private HttpStatus status = HttpStatus.OK;

        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }

        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        public Builder<T> timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder<T> status(HttpStatus status) {
            this.status = status;
            return this;
        }

        public ApiResponse<T> build() {
            return new ApiResponse<>(this);
        }

        public ResponseEntity<ApiResponse<T>> response() {
            return ResponseEntity.status(status).body(build());
        }
    }
}
