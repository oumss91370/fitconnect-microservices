package com.fitconnect.booking.exception;

/** Cours introuvable dans class-service (HTTP 404) */
public class ClassNotFoundException extends RuntimeException {
    public ClassNotFoundException(String message) {
        super(message);
    }
}
