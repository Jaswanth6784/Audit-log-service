package com.jaswanth.auditlog.redaction.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RedactionResult(
        UUID eventId,
        UUID receiptEventId,
        List<String> redactedPaths,
        Instant redactedAt) {
}
