package com.jaswanth.auditlog.redaction.application;

public class UnsupportedRedactionException extends RuntimeException {

    public UnsupportedRedactionException(short hashVersion) {
        super("Payload redaction requires hash version 2; event uses version " + hashVersion);
    }
}
