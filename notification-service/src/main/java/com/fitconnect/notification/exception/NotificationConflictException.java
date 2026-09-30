package com.fitconnect.notification.exception;

/** Notification déjà envoyée (HTTP 409) */
public class NotificationConflictException extends RuntimeException {
    public NotificationConflictException(String message) {
        super(message);
    }
}
