package com.jaswanth.auditlog.compliance.application;

import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.AccessReasonCode;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ComplianceAccessCommand(
        ComplianceIdentity identity,
        String accountId,
        AccessAction action,
        AccessOutcome outcome,
        List<ClientDataCategory> dataCategories,
        AccessPurpose purposeCode,
        UUID correlationId,
        AccessReasonCode reasonCode,
        Instant timestamp) {
}
