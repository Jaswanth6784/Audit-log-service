package com.jaswanth.auditlog.export.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditExportManifest(
        int bundleVersion,
        UUID bundleId,
        Instant generatedAt,
        ExportScopeDescriptor scope,
        ExportChainHead chainHead,
        long matchedEventCount,
        List<AuditExportRecord> records) {
}
