package com.fitconnect.payment.exception;

/** Paiement introuvable (HTTP 404) */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
