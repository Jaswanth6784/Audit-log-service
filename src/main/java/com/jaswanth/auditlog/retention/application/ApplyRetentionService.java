package com.jaswanth.auditlog.retention.application;

import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import com.jaswanth.auditlog.retention.configuration.RetentionProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Service
public class ApplyRetentionService {

    private final AuditEventRepository eventRepository;
    private final RetentionProperties properties;
    private final Clock clock;

    public ApplyRetentionService(
            AuditEventRepository eventRepository,
            RetentionProperties properties,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public RetentionRun archiveNextBatch() {
        var archivedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var cutoff = archivedAt.minus(properties.maxAge());
        var candidates = eventRepository.findUnarchivedSequencesRecordedBefore(
                cutoff,
                PageRequest.of(0, properties.batchSize() + 1));
        var hasMore = candidates.size() > properties.batchSize();
        var selected = hasMore ? candidates.subList(0, properties.batchSize()) : candidates;
        var archivedCount = selected.isEmpty()
                ? 0
                : eventRepository.archiveBySequenceNumbers(selected, archivedAt);
        return new RetentionRun(cutoff, archivedAt, archivedCount, hasMore);
    }
}
