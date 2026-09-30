package com.fitconnect.classservice.exception;

/** Nombre de places incohérent (HTTP 409) */
public class InvalidParticipantsException extends RuntimeException {
    public InvalidParticipantsException(String message) {
        super(message);
    }
}
