package com.jaswanth.auditlog.export.application;

import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventEntity;
import com.jaswanth.auditlog.export.model.ExportScopeDescriptor;

public record ExportScope(
        String actorId,
        String resourceType,
        String resourceId) {

    public ExportScope {
        var actorScope = hasText(actorId) && resourceType == null && resourceId == null;
        var resourceScope = actorId == null && hasText(resourceType) && hasText(resourceId);
        if (!actorScope && !resourceScope) {
            throw new InvalidExportScopeException(
                    "Specify either actorId or both resourceType and resourceId");
        }
    }

    public boolean matches(AuditEventEntity event) {
        if (actorId != null) {
            return actorId.equals(event.getActorId());
        }
        return resourceType.equals(event.getResourceType()) && resourceId.equals(event.getResourceId());
    }

    public ExportScopeDescriptor descriptor() {
        return actorId == null
                ? new ExportScopeDescriptor("RESOURCE", null, resourceType, resourceId)
                : new ExportScopeDescriptor("ACTOR", actorId, null, null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
