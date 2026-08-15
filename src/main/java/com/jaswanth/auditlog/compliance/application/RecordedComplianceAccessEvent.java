package com.jaswanth.auditlog.compliance.application;

import com.jaswanth.auditlog.audit.application.AppendedAuditEvent;
import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.AccessReasonCode;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;

import java.util.List;
import java.util.UUID;

public record RecordedComplianceAccessEvent(
        AppendedAuditEvent event,
        AccessAction action,
        AccessOutcome outcome,
        List<ClientDataCategory> dataCategories,
        AccessPurpose purposeCode,
        String sourceSystem,
        UUID correlationId,
        AccessReasonCode reasonCode) {
}
