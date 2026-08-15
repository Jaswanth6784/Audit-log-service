package com.jaswanth.auditlog.audit.application;

import java.time.Instant;

public record AuditEventQuery(
        String actorId,
        String resourceType,
        String resourceId,
        String eventType,
        Instant from,
        Instant to,
        long afterSequence,
        int limit) {

    public AuditEventQuery {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidAuditQueryException("'from' must be earlier than 'to'");
        }
    }
}
