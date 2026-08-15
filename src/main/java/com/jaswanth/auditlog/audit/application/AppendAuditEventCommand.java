package com.jaswanth.auditlog.audit.application;

import java.time.Instant;
import java.util.Map;

public record AppendAuditEventCommand(
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        Map<String, Object> payload,
        Instant timestamp) {
}
