package com.jaswanth.auditlog.audit.api;

import com.jaswanth.auditlog.audit.application.AuditEventView;
import com.jaswanth.auditlog.audit.application.AppendedAuditEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
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

    static AuditEventResponse from(AppendedAuditEvent event) {
        return new AuditEventResponse(
                event.sequenceNumber(),
                event.eventId(),
                event.eventType(),
                event.actorId(),
                event.resourceType(),
                event.resourceId(),
                event.payload(),
                event.timestamp(),
                event.recordedAt(),
                event.hashVersion(),
                event.contentHash(),
                event.previousHash(),
                event.recordHash());
    }

    static AuditEventResponse from(AuditEventView event) {
        return new AuditEventResponse(
                event.sequenceNumber(),
                event.eventId(),
                event.eventType(),
                event.actorId(),
                event.resourceType(),
                event.resourceId(),
                event.payload(),
                event.timestamp(),
                event.recordedAt(),
                event.hashVersion(),
                event.contentHash(),
                event.previousHash(),
                event.recordHash());
    }
}
