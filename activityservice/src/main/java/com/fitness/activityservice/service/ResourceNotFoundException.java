package com.fitness.activityservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an addressed resource (for example an activity id) does not exist.
 * Item endpoints must answer 404, not 500, so this is mapped by
 * {@code GlobalExceptionHandler} rather than surfacing as a bare RuntimeException.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
