package com.fitconnect.booking.exception;

/** Opération incompatible avec l'état ou les délais de la réservation (HTTP 409) */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}
