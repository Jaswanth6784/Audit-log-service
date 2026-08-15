package com.jaswanth.auditlog.audit.domain;

public class InvalidPayloadProofException extends RuntimeException {

    public InvalidPayloadProofException(String message) {
        super(message);
    }
}
