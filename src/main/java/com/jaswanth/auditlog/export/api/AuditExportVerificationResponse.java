package com.jaswanth.auditlog.export.api;

import com.jaswanth.auditlog.export.application.ExportVerificationResult;
import com.jaswanth.auditlog.export.application.ExportViolation;

public record AuditExportVerificationResponse(
        boolean valid,
        long checkedRecordCount,
        long fullRecordCount,
        Long firstInvalidSequence,
        ExportViolation violationType,
        String detail) {

    static AuditExportVerificationResponse from(ExportVerificationResult result) {
        return new AuditExportVerificationResponse(
                result.valid(),
                result.checkedRecordCount(),
                result.fullRecordCount(),
                result.firstInvalidSequence(),
                result.violationType(),
                result.detail());
    }
}
