package com.jaswanth.auditlog.export.application;

import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditChainHeadRepository;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import com.jaswanth.auditlog.export.configuration.ExportProperties;
import com.jaswanth.auditlog.export.infrastructure.ExportSignatureService;
import com.jaswanth.auditlog.export.model.AuditExportBundle;
import com.jaswanth.auditlog.export.model.AuditExportManifest;
import com.jaswanth.auditlog.export.model.ExportChainHead;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class CreateAuditExportService {

    private static final short GLOBAL_CHAIN_ID = 1;
    private static final int BUNDLE_VERSION = 1;

    private final AuditChainHeadRepository chainHeadRepository;
    private final AuditEventRepository eventRepository;
    private final ExportSignatureService signatureService;
    private final ExportProperties properties;
    private final Clock clock;

    public CreateAuditExportService(
            AuditChainHeadRepository chainHeadRepository,
            AuditEventRepository eventRepository,
            ExportSignatureService signatureService,
            ExportProperties properties,
            Clock clock) {
        this.chainHeadRepository = chainHeadRepository;
        this.eventRepository = eventRepository;
        this.signatureService = signatureService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AuditExportBundle create(ExportScope scope) {
        var head = chainHeadRepository.findById(GLOBAL_CHAIN_ID)
                .orElseThrow(() -> new IllegalStateException("Global audit chain head is missing"));
        if (head.getLastSequence() > properties.maxChainEvents()) {
            throw new ExportTooLargeException(properties.maxChainEvents());
        }

        final java.util.List<AuditExportRecord> records;
        try (var events = eventRepository.streamAllInSequenceOrder()) {
            records = events
                    .map(event -> AuditExportRecords.from(event, scope.matches(event)))
                    .toList();
        }
        if (records.size() != head.getLastSequence()) {
            throw new IllegalStateException("Audit chain changed or is incomplete during export");
        }

        var matchedEventCount = records.stream()
                .filter(record -> AuditExportRecord.FULL.equals(record.kind()))
                .count();
        var manifest = new AuditExportManifest(
                BUNDLE_VERSION,
                UUID.randomUUID(),
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                scope.descriptor(),
                new ExportChainHead(head.getLastSequence(), head.getLastHash()),
                matchedEventCount,
                records);
        return new AuditExportBundle(manifest, signatureService.sign(manifest));
    }
}
