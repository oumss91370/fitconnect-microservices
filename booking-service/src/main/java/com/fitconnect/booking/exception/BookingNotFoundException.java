package com.fitconnect.booking.exception;

/** Réservation introuvable (HTTP 404) */
public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String message) {
        super(message);
    }
}
