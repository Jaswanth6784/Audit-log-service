package com.jaswanth.auditlog.compliance.api;

import com.jaswanth.auditlog.compliance.application.RecordedComplianceAccessEvent;
import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.AccessReasonCode;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ComplianceAccessEventResponse(
        long sequenceNumber,
        UUID eventId,
        String actorId,
        String accountId,
        AccessAction action,
        AccessOutcome outcome,
        List<ClientDataCategory> dataCategories,
        AccessPurpose purposeCode,
        String sourceSystem,
        UUID correlationId,
        AccessReasonCode reasonCode,
        Instant timestamp,
        Instant recordedAt,
        short hashVersion,
        String recordHash) {

    static ComplianceAccessEventResponse from(RecordedComplianceAccessEvent recorded) {
        var event = recorded.event();
        return new ComplianceAccessEventResponse(
                event.sequenceNumber(),
                event.eventId(),
                event.actorId(),
                event.resourceId(),
                recorded.action(),
                recorded.outcome(),
                recorded.dataCategories(),
                recorded.purposeCode(),
                recorded.sourceSystem(),
                recorded.correlationId(),
                recorded.reasonCode(),
                event.timestamp(),
                event.recordedAt(),
                event.hashVersion(),
                event.recordHash());
    }
}
