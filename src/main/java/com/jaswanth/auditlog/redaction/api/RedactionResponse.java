package com.jaswanth.auditlog.redaction.api;

import com.jaswanth.auditlog.redaction.application.RedactionResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RedactionResponse(
        UUID eventId,
        UUID receiptEventId,
        List<String> redactedPaths,
        Instant redactedAt) {

    static RedactionResponse from(RedactionResult result) {
        return new RedactionResponse(
                result.eventId(),
                result.receiptEventId(),
                result.redactedPaths(),
                result.redactedAt());
    }
}
