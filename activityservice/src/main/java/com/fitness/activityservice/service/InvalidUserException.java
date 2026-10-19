package com.fitness.activityservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a write is submitted for a userId that userservice does not know.
 * That is a client error, so it must answer 400 rather than 500.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class

InvalidUserException extends RuntimeException {

    public InvalidUserException(String message) {
        super(message);
    }
}
