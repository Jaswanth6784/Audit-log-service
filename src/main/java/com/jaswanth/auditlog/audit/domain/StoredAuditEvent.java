package com.jaswanth.auditlog.audit.domain;

public record StoredAuditEvent(
        long sequenceNumber,
        short hashVersion,
        AuditEventContent content,
        String contentHash,
        String previousHash,
        String recordHash) {
}
