package com.jaswanth.auditlog.export.model;

public record AuditExportBundle(
        AuditExportManifest manifest,
        AuditExportSignature signature) {
}
