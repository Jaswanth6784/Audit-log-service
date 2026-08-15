package com.jaswanth.auditlog.compliance.export.model;

import com.jaswanth.auditlog.export.model.AuditExportSignature;

public record ComplianceExportBundle(
        ComplianceExportManifest manifest,
        AuditExportSignature signature) {
}
