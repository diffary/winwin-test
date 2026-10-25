package com.winwin.authapi.common;

/** Domain exceptions that GlobalExceptionHandler maps to HTTP statuses. */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    public static class EmailTakenException extends RuntimeException {
        public EmailTakenException() {
            super("email already registered");
        }
    }

    public static class DataApiException extends RuntimeException {
        public DataApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
