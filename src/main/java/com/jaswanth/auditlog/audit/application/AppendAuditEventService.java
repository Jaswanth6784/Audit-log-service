package com.jaswanth.auditlog.audit.application;

import com.jaswanth.auditlog.audit.domain.AuditEventContent;
import com.jaswanth.auditlog.audit.domain.AuditHashService;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditChainHeadRepository;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventEntity;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AppendAuditEventService {

    private static final short GLOBAL_CHAIN_ID = 1;

    private final AuditChainHeadRepository chainHeadRepository;
    private final AuditEventRepository eventRepository;
    private final AuditHashService hashService;
    private final Clock clock;

    public AppendAuditEventService(
            AuditChainHeadRepository chainHeadRepository,
            AuditEventRepository eventRepository,
            AuditHashService hashService,
            Clock clock) {
        this.chainHeadRepository = chainHeadRepository;
        this.eventRepository = eventRepository;
        this.hashService = hashService;
        this.clock = clock;
    }

    @Transactional
    public AppendedAuditEvent append(AppendAuditEventCommand command) {
        var chainHead = chainHeadRepository.findByChainIdForUpdate(GLOBAL_CHAIN_ID)
                .orElseThrow(() -> new IllegalStateException("Global audit chain head is missing"));

        var recordedAt = normalizeTimestamp(clock.instant());
        var timestamp = command.timestamp() == null
                ? recordedAt
                : normalizeTimestamp(command.timestamp());
        var content = new AuditEventContent(
                command.eventType(),
                command.actorId(),
                command.resourceType(),
                command.resourceId(),
                command.payload(),
                timestamp);
        var hashes = hashService.calculateCurrent(content, chainHead.getLastHash());

        var entity = AuditEventEntity.create(
                UUID.randomUUID(),
                content,
                recordedAt,
                hashes);
        eventRepository.saveAndFlush(entity);
        chainHead.advance(entity.getSequenceNumber(), hashes.recordHash());

        return new AppendedAuditEvent(
                entity.getSequenceNumber(),
                entity.getEventId(),
                entity.getEventType(),
                entity.getActorId(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getPayload(),
                entity.getOccurredAt(),
                entity.getRecordedAt(),
                entity.getHashVersion(),
                entity.getContentHash(),
                entity.getPreviousHash(),
                entity.getRecordHash());
    }

    private Instant normalizeTimestamp(Instant timestamp) {
        return timestamp.truncatedTo(ChronoUnit.MICROS);
    }
}
