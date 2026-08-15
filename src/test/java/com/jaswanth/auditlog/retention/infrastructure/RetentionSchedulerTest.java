package com.jaswanth.auditlog.retention.infrastructure;

import com.jaswanth.auditlog.retention.application.ApplyRetentionService;
import com.jaswanth.auditlog.retention.application.RetentionRun;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionSchedulerTest {

    @Test
    void delegatesOneBoundedBatchPerScheduledInvocation() {
        var retentionService = mock(ApplyRetentionService.class);
        when(retentionService.archiveNextBatch()).thenReturn(new RetentionRun(
                Instant.parse("2025-08-15T00:00:00Z"),
                Instant.parse("2026-08-15T00:00:00Z"),
                25,
                true));

        new RetentionScheduler(retentionService).archiveExpiredEvents();

        verify(retentionService).archiveNextBatch();
    }
}
