package com.jaswanth.auditlog.redaction.application;

public class InvalidRedactionException extends RuntimeException {

    public InvalidRedactionException(String message) {
        super(message);
    }
}
