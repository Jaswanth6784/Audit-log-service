package com.jaswanth.auditlog.retention.application;

import java.time.Instant;

public record RetentionRun(
        Instant cutoff,
        Instant archivedAt,
        int archivedCount,
        boolean hasMoreEligibleEvents) {
}
