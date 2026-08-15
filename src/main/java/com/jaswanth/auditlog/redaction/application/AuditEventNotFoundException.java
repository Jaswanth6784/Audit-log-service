package com.jaswanth.auditlog.redaction.application;

import java.util.UUID;

public class AuditEventNotFoundException extends RuntimeException {

    public AuditEventNotFoundException(UUID eventId) {
        super("Audit event not found: " + eventId);
    }
}
