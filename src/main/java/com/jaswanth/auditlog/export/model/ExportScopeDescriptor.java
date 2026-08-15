package com.jaswanth.auditlog.export.model;

public record ExportScopeDescriptor(
        String type,
        String actorId,
        String resourceType,
        String resourceId) {
}
