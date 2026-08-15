package com.jaswanth.auditlog.retention.infrastructure;

import com.jaswanth.auditlog.retention.application.ApplyRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "audit.retention", name = "enabled", havingValue = "true")
public class RetentionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionScheduler.class);

    private final ApplyRetentionService retentionService;

    public RetentionScheduler(ApplyRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(fixedDelayString = "${audit.retention.fixed-delay:1h}")
    public void archiveExpiredEvents() {
        var result = retentionService.archiveNextBatch();
        LOGGER.info(
                "Retention batch completed: archivedCount={}, cutoff={}, hasMoreEligibleEvents={}",
                result.archivedCount(),
                result.cutoff(),
                result.hasMoreEligibleEvents());
    }
}
