package com.jaswanth.auditlog.audit.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventView(
        long sequenceNumber,
        UUID eventId,
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        Map<String, Object> payload,
        Instant timestamp,
        Instant recordedAt,
        short hashVersion,
        String contentHash,
        String previousHash,
        String recordHash) {
}
