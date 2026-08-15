package com.jaswanth.auditlog.export.application;

import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventEntity;
import com.jaswanth.auditlog.export.model.AuditExportRecord;

public final class AuditExportRecords {

    private AuditExportRecords() {
    }

    public static AuditExportRecord from(AuditEventEntity event, boolean includeContent) {
        return new AuditExportRecord(
                includeContent ? AuditExportRecord.FULL : AuditExportRecord.BRIDGE,
                event.getSequenceNumber(),
                includeContent ? event.getEventId() : null,
                includeContent ? event.getEventType() : null,
                includeContent ? event.getActorId() : null,
                includeContent ? event.getResourceType() : null,
                includeContent ? event.getResourceId() : null,
                includeContent ? event.getPayload() : null,
                includeContent ? event.getPayloadProofs() : null,
                includeContent ? event.getOccurredAt() : null,
                includeContent ? event.getRecordedAt() : null,
                event.getHashVersion(),
                event.getContentHash(),
                event.getPreviousHash(),
                event.getRecordHash());
    }
}
