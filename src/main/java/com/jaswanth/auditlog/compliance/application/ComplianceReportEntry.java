package com.jaswanth.auditlog.compliance.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ComplianceReportEntry(
        long sequenceNumber,
        UUID eventId,
        String actorId,
        String accountId,
        String action,
        String outcome,
        List<String> dataCategories,
        String purposeCode,
        String sourceSystem,
        String correlationId,
        String reasonCode,
        Instant timestamp,
        Instant recordedAt,
        boolean redacted,
        short hashVersion,
        String previousHash,
        String recordHash) {
}
