package com.jaswanth.auditlog.compliance.export.model;

import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;

import java.time.Instant;

public record ComplianceExportCriteria(
        String accountId,
        String actorId,
        Instant from,
        Instant to,
        AccessPurpose reportPurpose,
        AccessAction action,
        AccessOutcome outcome,
        String sourceSystem,
        ClientDataCategory dataCategory) {
}
