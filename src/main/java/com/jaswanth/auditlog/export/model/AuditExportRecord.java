package com.jaswanth.auditlog.export.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditExportRecord(
        String kind,
        long sequenceNumber,
        UUID eventId,
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        Map<String, Object> payload,
        Map<String, Object> payloadProofs,
        Instant timestamp,
        Instant recordedAt,
        short hashVersion,
        String contentHash,
        String previousHash,
        String recordHash) {

    public static final String FULL = "FULL";
    public static final String BRIDGE = "BRIDGE";
}
