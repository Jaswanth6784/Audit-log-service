package com.jaswanth.auditlog.audit.application;

import java.util.List;

public record AuditEventPage(
        List<AuditEventView> items,
        int limit,
        boolean hasMore,
        long nextAfterSequence) {
}
