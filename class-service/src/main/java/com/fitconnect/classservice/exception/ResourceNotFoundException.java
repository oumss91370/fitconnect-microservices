package com.fitconnect.classservice.exception;

/** Cours introuvable (HTTP 404) */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
