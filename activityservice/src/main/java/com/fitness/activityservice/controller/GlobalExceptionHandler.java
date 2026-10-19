package com.fitness.activityservice.controller;

import com.fitness.activityservice.service.InvalidUserException;
import com.fitness.activityservice.service.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Converts unhandled exceptions into a consistent JSON error response instead of
 * leaking Spring's default error handling (which omits `message` because
 * `server.error.include-message` defaults to `never`).
 *
 * <pre>
 * { "timestamp": "...", "status": 404, "error": "Not Found", "message": "..." }
 * </pre>
 *
 * Extending ResponseEntityExceptionHandler keeps the correct status codes for
 * framework-level failures (missing header / unreadable body / wrong method must
 * stay 400/405). Without it the catch-all below would turn them into 500s,
 * because it would be the only registered handler for them.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Unknown activity id (or any other missing resource) -> 404, not 500. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    /** Activity submitted for a userId userservice rejects -> 400, not 500. */
    @ExceptionHandler(InvalidUserException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidUser(InvalidUserException ex) {
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, Exception ex) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
        ));
    }
}
