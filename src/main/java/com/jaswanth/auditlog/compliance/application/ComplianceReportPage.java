package com.jaswanth.auditlog.compliance.application;

import java.util.List;
import java.util.UUID;

public record ComplianceReportPage(
        List<ComplianceReportEntry> items,
        int requestedLimit,
        boolean hasMore,
        long nextAfterSequence,
        long capturedThroughSequence,
        String criteriaHash,
        UUID accessReceiptEventId) {
}
