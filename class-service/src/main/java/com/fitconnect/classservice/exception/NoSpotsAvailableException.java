package com.fitconnect.classservice.exception;

/** Plus assez de places pour satisfaire la demande (HTTP 409) */
public class NoSpotsAvailableException extends RuntimeException {
    public NoSpotsAvailableException(String message) {
        super(message);
    }
}
