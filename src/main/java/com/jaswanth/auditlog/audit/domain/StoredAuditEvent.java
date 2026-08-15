package com.jaswanth.auditlog.audit.domain;

import java.util.Map;

public record StoredAuditEvent(
        long sequenceNumber,
        short hashVersion,
        AuditEventContent content,
        String contentHash,
        String previousHash,
        String recordHash,
        Map<String, Object> payloadProofs) {

    public StoredAuditEvent(
            long sequenceNumber,
            short hashVersion,
            AuditEventContent content,
            String contentHash,
            String previousHash,
            String recordHash) {
        this(sequenceNumber, hashVersion, content, contentHash, previousHash, recordHash, Map.of());
    }
}
