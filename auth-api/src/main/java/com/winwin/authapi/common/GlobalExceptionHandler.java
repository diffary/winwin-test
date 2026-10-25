package com.winwin.authapi.common;

import com.winwin.authapi.common.ApiExceptions.DataApiException;
import com.winwin.authapi.common.ApiExceptions.EmailTakenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Turns exceptions into a uniform {"error": "..."} body. Never leaks stack traces or secrets. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> onValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse("invalid request");
        return body(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> onUnreadable() {
        return body(HttpStatus.BAD_REQUEST, "malformed JSON body");
    }

    @ExceptionHandler(EmailTakenException.class)
    ResponseEntity<Map<String, String>> onEmailTaken(EmailTakenException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String, String>> onBadCredentials() {
        return body(HttpStatus.UNAUTHORIZED, "invalid email or password");
    }

    @ExceptionHandler(DataApiException.class)
    ResponseEntity<Map<String, String>> onDataApi(DataApiException e) {
        log.warn("data-api call failed: {}", e.getMessage());
        return body(HttpStatus.BAD_GATEWAY, "processing service unavailable");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
