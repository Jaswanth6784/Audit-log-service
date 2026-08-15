package com.jaswanth.auditlog.audit.domain;

import java.time.Instant;
import java.util.Map;

public record AuditEventContent(
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        Map<String, Object> payload,
        Instant timestamp) {
}
