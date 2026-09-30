package com.fitconnect.notification.exception;

/** Notification introuvable (HTTP 404) */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
