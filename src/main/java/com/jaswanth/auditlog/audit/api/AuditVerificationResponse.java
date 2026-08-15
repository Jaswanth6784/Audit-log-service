package com.jaswanth.auditlog.audit.api;

import com.jaswanth.auditlog.audit.domain.AuditChainVerification;
import com.jaswanth.auditlog.audit.domain.AuditChainViolation;

public record AuditVerificationResponse(
        boolean valid,
        long verifiedEventCount,
        Long chainHeadSequence,
        Long firstInvalidSequence,
        AuditChainViolation violationType,
        String detail) {

    static AuditVerificationResponse from(AuditChainVerification verification) {
        return new AuditVerificationResponse(
                verification.valid(),
                verification.verifiedEventCount(),
                verification.chainHeadSequence(),
                verification.firstInvalidSequence(),
                verification.violationType(),
                verification.detail());
    }
}
