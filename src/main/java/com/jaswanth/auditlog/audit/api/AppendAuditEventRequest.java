package com.jaswanth.auditlog.audit.api;

import com.jaswanth.auditlog.audit.application.AppendAuditEventCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public record AppendAuditEventRequest(
        @NotBlank @Size(max = 100) String eventType,
        @NotBlank @Size(max = 255) String actorId,
        @NotBlank @Size(max = 100) String resourceType,
        @NotBlank @Size(max = 255) String resourceId,
        @NotNull @Size(max = 100) Map<String, Object> payload,
        Instant timestamp) {

    AppendAuditEventCommand toCommand() {
        return new AppendAuditEventCommand(eventType, actorId, resourceType, resourceId, payload, timestamp);
    }
}
