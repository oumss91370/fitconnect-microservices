package com.fitconnect.classservice.exception;

/** Conflit de version persistant après plusieurs tentatives (HTTP 409) */
public class ConcurrentUpdateException extends RuntimeException {
    public ConcurrentUpdateException(String message) {
        super(message);
    }
}
