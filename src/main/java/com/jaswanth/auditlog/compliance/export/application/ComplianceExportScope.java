package com.jaswanth.auditlog.compliance.export.application;

import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventEntity;
import com.jaswanth.auditlog.compliance.application.InvalidComplianceReportQueryException;
import com.jaswanth.auditlog.compliance.application.RecordComplianceAccessService;
import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;
import com.jaswanth.auditlog.compliance.export.model.ComplianceExportCriteria;
import com.jaswanth.auditlog.export.model.AuditExportRecord;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ComplianceExportScope(
        String accountId,
        String actorId,
        Instant from,
        Instant to,
        AccessPurpose reportPurpose,
        AccessAction action,
        AccessOutcome outcome,
        String sourceSystem,
        ClientDataCategory dataCategory) {

    public ComplianceExportScope {
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
        if (sourceSystem != null && sourceSystem.isBlank()) {
            throw new InvalidComplianceReportQueryException("'sourceSystem' must not be blank");
        }
    }

    public static ComplianceExportScope from(ComplianceExportCriteria criteria) {
        if (criteria == null) {
            throw new InvalidComplianceReportQueryException("Compliance export criteria are required");
        }
        return new ComplianceExportScope(
                criteria.accountId(), criteria.actorId(), criteria.from(), criteria.to(),
                criteria.reportPurpose(), criteria.action(), criteria.outcome(),
                criteria.sourceSystem(), criteria.dataCategory());
    }

    public ComplianceExportCriteria descriptor() {
        return new ComplianceExportCriteria(
                accountId, actorId, from, to, reportPurpose, action, outcome, sourceSystem, dataCategory);
    }

    public boolean matches(AuditEventEntity event) {
        return matches(
                event.getEventType(), event.getActorId(), event.getResourceType(), event.getResourceId(),
                event.getPayload(), event.getOccurredAt());
    }

    public boolean matches(AuditExportRecord record) {
        return matches(
                record.eventType(), record.actorId(), record.resourceType(), record.resourceId(),
                record.payload(), record.timestamp());
    }

    private boolean matches(
            String eventType,
            String eventActorId,
            String resourceType,
            String resourceId,
            Map<String, Object> payload,
            Instant timestamp) {
        if (!RecordComplianceAccessService.EVENT_TYPE.equals(eventType)
                || !RecordComplianceAccessService.RESOURCE_TYPE.equals(resourceType)
                || timestamp == null || timestamp.isBefore(from) || !timestamp.isBefore(to)) {
            return false;
        }
        if (accountId != null ? !accountId.equals(resourceId) : !actorId.equals(eventActorId)) {
            return false;
        }
        return payload != null
                && matches(payload.get("action"), action)
                && matches(payload.get("outcome"), outcome)
                && matches(payload.get("sourceSystem"), sourceSystem)
                && matchesCategory(payload.get("dataCategories"));
    }

    private boolean matches(Object actual, Enum<?> expected) {
        return expected == null || expected.name().equals(actual);
    }

    private boolean matches(Object actual, String expected) {
        return expected == null || expected.equals(actual);
    }

    private boolean matchesCategory(Object actual) {
        return dataCategory == null
                || actual instanceof List<?> values && values.contains(dataCategory.name());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
