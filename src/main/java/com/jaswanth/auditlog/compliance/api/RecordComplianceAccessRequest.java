package com.jaswanth.auditlog.compliance.api;

import com.jaswanth.auditlog.compliance.application.ComplianceAccessCommand;
import com.jaswanth.auditlog.compliance.application.ComplianceIdentity;
import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.AccessReasonCode;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecordComplianceAccessRequest(
        @NotBlank @Size(max = 255) String accountId,
        @NotNull AccessAction action,
        @NotNull AccessOutcome outcome,
        @NotEmpty @Size(max = 20) List<@NotNull ClientDataCategory> dataCategories,
        @NotNull AccessPurpose purposeCode,
        @NotNull UUID correlationId,
        AccessReasonCode reasonCode,
        Instant timestamp) {

    ComplianceAccessCommand toCommand(ComplianceIdentity identity) {
        return new ComplianceAccessCommand(
                identity,
                accountId,
                action,
                outcome,
                List.copyOf(dataCategories),
                purposeCode,
                correlationId,
                reasonCode,
                timestamp);
    }
}
