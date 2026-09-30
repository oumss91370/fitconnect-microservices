package com.fitconnect.classservice.exception;

/** Cours annulé, terminé ou passé (HTTP 409) */
public class ClassNotBookableException extends RuntimeException {
    public ClassNotBookableException(String message) {
        super(message);
    }
}
