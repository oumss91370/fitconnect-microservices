package com.fitconnect.booking.exception;

/** Service distant indisponible ou circuit ouvert (HTTP 503) */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
