package com.jaswanth.auditlog.audit.domain;

public record AuditChainVerification(
        boolean valid,
        long verifiedEventCount,
        Long chainHeadSequence,
        Long firstInvalidSequence,
        AuditChainViolation violationType,
        String detail) {

    public static AuditChainVerification valid(long verifiedEventCount, long chainHeadSequence) {
        return new AuditChainVerification(
                true,
                verifiedEventCount,
                chainHeadSequence,
                null,
                null,
                "Audit chain is valid");
    }

    public static AuditChainVerification invalid(
            long verifiedEventCount,
            Long chainHeadSequence,
            Long firstInvalidSequence,
            AuditChainViolation violationType,
            String detail) {
        return new AuditChainVerification(
                false,
                verifiedEventCount,
                chainHeadSequence,
                firstInvalidSequence,
                violationType,
                detail);
    }
}
