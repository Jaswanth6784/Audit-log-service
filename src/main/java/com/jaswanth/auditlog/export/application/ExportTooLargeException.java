package com.jaswanth.auditlog.export.application;

public class ExportTooLargeException extends RuntimeException {

    public ExportTooLargeException(int limit) {
        super("Audit chain exceeds the synchronous export limit of " + limit + " events");
    }
}
