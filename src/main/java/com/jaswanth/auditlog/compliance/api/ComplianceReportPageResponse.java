package com.jaswanth.auditlog.compliance.api;

import com.jaswanth.auditlog.compliance.application.ComplianceReportEntry;
import com.jaswanth.auditlog.compliance.application.ComplianceReportPage;

import java.util.List;
import java.util.UUID;

public record ComplianceReportPageResponse(
        List<ComplianceReportEntry> items,
        int requestedLimit,
        boolean hasMore,
        long nextAfterSequence,
        long capturedThroughSequence,
        String criteriaHash,
        UUID accessReceiptEventId) {

    static ComplianceReportPageResponse from(ComplianceReportPage page) {
        return new ComplianceReportPageResponse(
                page.items(),
                page.requestedLimit(),
                page.hasMore(),
                page.nextAfterSequence(),
                page.capturedThroughSequence(),
                page.criteriaHash(),
                page.accessReceiptEventId());
    }
}
