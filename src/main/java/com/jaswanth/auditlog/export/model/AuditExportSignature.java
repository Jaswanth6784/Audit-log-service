package com.jaswanth.auditlog.export.model;

public record AuditExportSignature(
        String algorithm,
        String keyId,
        String publicKey,
        String value) {
}
