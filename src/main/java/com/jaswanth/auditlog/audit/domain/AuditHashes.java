package com.jaswanth.auditlog.audit.domain;

import java.util.Map;

public record AuditHashes(
        short hashVersion,
        String contentHash,
        String previousHash,
        String recordHash,
        Map<String, Object> canonicalPayload,
        Map<String, Object> payloadProofs) {
}
