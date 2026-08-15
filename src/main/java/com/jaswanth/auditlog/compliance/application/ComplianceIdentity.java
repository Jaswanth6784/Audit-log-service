package com.jaswanth.auditlog.compliance.application;

public record ComplianceIdentity(
        String actorId,
        String sourceSystem) {
}
