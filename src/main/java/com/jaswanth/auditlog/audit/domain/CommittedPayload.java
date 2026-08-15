package com.jaswanth.auditlog.audit.domain;

import java.util.Map;

public record CommittedPayload(
        Map<String, Object> payload,
        Map<String, Object> commitmentTree,
        Map<String, Object> proofs) {
}
