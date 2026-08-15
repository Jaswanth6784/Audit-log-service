package com.jaswanth.auditlog.audit.api;

import com.jaswanth.auditlog.audit.application.AuditEventPage;

import java.util.List;

public record AuditEventPageResponse(
        List<AuditEventResponse> items,
        int count,
        int limit,
        boolean hasMore,
        long nextAfterSequence) {

    static AuditEventPageResponse from(AuditEventPage page) {
        return new AuditEventPageResponse(
                page.items().stream().map(AuditEventResponse::from).toList(),
                page.items().size(),
                page.limit(),
                page.hasMore(),
                page.nextAfterSequence());
    }
}
