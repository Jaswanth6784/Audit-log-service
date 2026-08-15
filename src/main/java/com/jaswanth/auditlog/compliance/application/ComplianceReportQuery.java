package com.jaswanth.auditlog.compliance.application;

import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;

import java.time.Instant;

public record ComplianceReportQuery(
        String accountId,
        String actorId,
        Instant from,
        Instant to,
        AccessPurpose reportPurpose,
        AccessAction action,
        AccessOutcome outcome,
        String sourceSystem,
        ClientDataCategory dataCategory,
        long afterSequence,
        int limit) {

    public ComplianceReportQuery {
        var hasAccount = hasText(accountId);
        var hasActor = hasText(actorId);
        if (hasAccount == hasActor) {
            throw new InvalidComplianceReportQueryException(
                    "Specify exactly one primary scope: accountId or actorId");
        }
        if (from == null || to == null) {
            throw new InvalidComplianceReportQueryException("Both 'from' and 'to' are required");
        }
        if (!from.isBefore(to)) {
            throw new InvalidComplianceReportQueryException("'from' must be earlier than 'to'");
        }
        if (reportPurpose == null) {
            throw new InvalidComplianceReportQueryException("'reportPurpose' is required");
        }
    }

    public String scopeType() {
        return accountId == null ? "ACTOR" : "ACCOUNT";
    }

    public String scopeValue() {
        return accountId == null ? actorId : accountId;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
