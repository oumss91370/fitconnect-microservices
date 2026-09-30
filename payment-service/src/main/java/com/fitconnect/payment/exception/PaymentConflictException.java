package com.fitconnect.payment.exception;

/** Paiement déjà effectué / remboursement impossible (HTTP 409) */
public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException(String message) {
        super(message);
    }
}
