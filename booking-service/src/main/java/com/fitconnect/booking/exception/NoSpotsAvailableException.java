package com.fitconnect.booking.exception;

/** Plus assez de places disponibles (HTTP 409) */
public class NoSpotsAvailableException extends RuntimeException {
    public NoSpotsAvailableException(String message) {
        super(message);
    }
}
