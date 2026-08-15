package com.jaswanth.auditlog.redaction.application;

import com.jaswanth.auditlog.audit.application.AppendAuditEventCommand;
import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
import com.jaswanth.auditlog.audit.domain.AuditHashService;
import com.jaswanth.auditlog.audit.domain.InvalidPayloadProofException;
import com.jaswanth.auditlog.audit.domain.PayloadCommitmentService;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class RedactAuditEventService {

    private final AuditEventRepository eventRepository;
    private final PayloadCommitmentService commitmentService;
    private final AppendAuditEventService appendService;
    private final Clock clock;

    public RedactAuditEventService(
            AuditEventRepository eventRepository,
            PayloadCommitmentService commitmentService,
            AppendAuditEventService appendService,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.commitmentService = commitmentService;
        this.appendService = appendService;
        this.clock = clock;
    }

    @Transactional
    public RedactionResult redact(
            UUID eventId,
            Collection<String> requestedPaths,
            String actorId,
            String reason) {
        var event = eventRepository.findByEventIdForUpdate(eventId)
                .orElseThrow(() -> new AuditEventNotFoundException(eventId));
        if (event.getHashVersion() != AuditHashService.CURRENT_HASH_VERSION) {
            throw new UnsupportedRedactionException(event.getHashVersion());
        }

        var paths = requestedPaths.stream().distinct().sorted().toList();
        try {
            var redacted = commitmentService.redact(event.getPayload(), event.getPayloadProofs(), paths);
            var redactedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            event.redact(redacted.payload(), redacted.proofs(), redactedAt);
            eventRepository.saveAndFlush(event);
            var receipt = appendService.append(new AppendAuditEventCommand(
                    "AUDIT_PAYLOAD_REDACTED",
                    actorId,
                    "AUDIT_EVENT",
                    eventId.toString(),
                    Map.of(
                            "targetEventId", eventId.toString(),
                            "paths", paths,
                            "commitments", commitmentsFor(redacted.proofs(), paths),
                            "reason", reason),
                    redactedAt));
            return new RedactionResult(eventId, receipt.eventId(), paths, redactedAt);
        } catch (InvalidPayloadProofException exception) {
            throw new InvalidRedactionException(exception.getMessage());
        }
    }

    private Map<String, Object> commitmentsFor(Map<String, Object> proofs, Collection<String> paths) {
        var commitments = new TreeMap<String, Object>();
        for (var path : paths) {
            commitments.put(path, proofAt(proofs, path).get("commitment"));
        }
        return commitments;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> proofAt(Map<String, Object> proofs, String path) {
        return (Map<String, Object>) proofs.get(path);
    }
}
