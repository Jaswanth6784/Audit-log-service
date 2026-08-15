package com.jaswanth.auditlog.export.application;

public record ExportVerificationResult(
        boolean valid,
        long checkedRecordCount,
        long fullRecordCount,
        Long firstInvalidSequence,
        ExportViolation violationType,
        String detail) {

    public static ExportVerificationResult valid(long checked, long full) {
        return new ExportVerificationResult(true, checked, full, null, null, null);
    }

    public static ExportVerificationResult invalid(
            long checked,
            long full,
            Long sequence,
            ExportViolation violation,
            String detail) {
        return new ExportVerificationResult(false, checked, full, sequence, violation, detail);
    }
}
