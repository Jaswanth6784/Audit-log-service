package com.jaswanth.auditlog.compliance.export.model;

import com.jaswanth.auditlog.export.model.AuditExportRecord;
import com.jaswanth.auditlog.export.model.ExportChainHead;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ComplianceExportManifest(
        int bundleVersion,
        String bundleType,
        UUID bundleId,
        Instant generatedAt,
        String criteriaHash,
        ComplianceExportCriteria criteria,
        ExportChainHead chainHead,
        long matchedEventCount,
        List<AuditExportRecord> records) {
}
