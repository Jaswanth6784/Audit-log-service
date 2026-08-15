package com.jaswanth.auditlog.audit.application;

import com.jaswanth.auditlog.audit.domain.AuditChainVerification;
import com.jaswanth.auditlog.audit.domain.AuditChainVerifier;
import com.jaswanth.auditlog.audit.domain.AuditChainViolation;
import com.jaswanth.auditlog.audit.domain.AuditEventContent;
import com.jaswanth.auditlog.audit.domain.StoredAuditEvent;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditChainHeadRepository;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventEntity;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerifyAuditChainService {

    private static final short GLOBAL_CHAIN_ID = 1;

    private final AuditChainHeadRepository chainHeadRepository;
    private final AuditEventRepository eventRepository;
    private final AuditChainVerifier chainVerifier;

    public VerifyAuditChainService(
            AuditChainHeadRepository chainHeadRepository,
            AuditEventRepository eventRepository,
            AuditChainVerifier chainVerifier) {
        this.chainHeadRepository = chainHeadRepository;
        this.eventRepository = eventRepository;
        this.chainVerifier = chainVerifier;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AuditChainVerification verify() {
        var chainHead = chainHeadRepository.findById(GLOBAL_CHAIN_ID).orElse(null);
        if (chainHead == null) {
            return AuditChainVerification.invalid(
                    0,
                    null,
                    null,
                    AuditChainViolation.CHAIN_HEAD_MISSING,
                    "Global audit chain head is missing");
        }

        try (var events = eventRepository.streamAllInSequenceOrder()) {
            return chainVerifier.verify(
                    events.map(this::toStoredEvent).iterator(),
                    chainHead.getLastSequence(),
                    chainHead.getLastHash());
        }
    }

    private StoredAuditEvent toStoredEvent(AuditEventEntity entity) {
        var content = new AuditEventContent(
                entity.getEventType(),
                entity.getActorId(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getPayload(),
                entity.getOccurredAt());
        return new StoredAuditEvent(
                entity.getSequenceNumber(),
                entity.getHashVersion(),
                content,
                entity.getContentHash(),
                entity.getPreviousHash(),
                entity.getRecordHash(),
                entity.getPayloadProofs());
    }
}
