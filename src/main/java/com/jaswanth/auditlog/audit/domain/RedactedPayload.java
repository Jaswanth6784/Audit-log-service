package com.jaswanth.auditlog.audit.domain;

import java.util.Map;

public record RedactedPayload(
        Map<String, Object> payload,
        Map<String, Object> proofs) {
}
