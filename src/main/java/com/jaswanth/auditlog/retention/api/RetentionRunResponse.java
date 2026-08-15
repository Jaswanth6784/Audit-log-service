package com.jaswanth.auditlog.retention.api;

import com.jaswanth.auditlog.retention.application.RetentionRun;

import java.time.Instant;

public record RetentionRunResponse(
        Instant cutoff,
        Instant archivedAt,
        int archivedCount,
        boolean hasMoreEligibleEvents) {

    static RetentionRunResponse from(RetentionRun run) {
        return new RetentionRunResponse(
                run.cutoff(),
                run.archivedAt(),
                run.archivedCount(),
                run.hasMoreEligibleEvents());
    }
}
