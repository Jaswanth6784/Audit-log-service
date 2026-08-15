package com.jaswanth.auditlog.compliance.export.application;

import com.jaswanth.auditlog.audit.application.AppendAuditEventCommand;
import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditChainHeadRepository;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import com.jaswanth.auditlog.compliance.export.model.ComplianceExportBundle;
import com.jaswanth.auditlog.compliance.export.model.ComplianceExportManifest;
import com.jaswanth.auditlog.export.application.AuditExportRecords;
import com.jaswanth.auditlog.export.application.ExportTooLargeException;
import com.jaswanth.auditlog.export.configuration.ExportProperties;
import com.jaswanth.auditlog.export.infrastructure.ExportSignatureService;
import com.jaswanth.auditlog.export.model.AuditExportRecord;
import com.jaswanth.auditlog.export.model.ExportChainHead;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
public class CreateComplianceExportService {

    public static final String BUNDLE_TYPE = "COMPLIANCE_ACCESS_REPORT";
    public static final String RECEIPT_EVENT_TYPE = "COMPLIANCE_ACCESS_REPORT_EXPORTED";
    public static final String RECEIPT_RESOURCE_TYPE = "COMPLIANCE_ACCESS_EXPORT";
    private static final short GLOBAL_CHAIN_ID = 1;
    private static final int BUNDLE_VERSION = 1;

    private final AuditChainHeadRepository chainHeadRepository;
    private final AuditEventRepository eventRepository;
    private final ExportSignatureService signatureService;
    private final ComplianceExportCriteriaHasher criteriaHasher;
    private final AppendAuditEventService appendService;
    private final ExportProperties properties;
    private final EntityManager entityManager;
    private final Clock clock;

    public CreateComplianceExportService(
            AuditChainHeadRepository chainHeadRepository,
            AuditEventRepository eventRepository,
            ExportSignatureService signatureService,
            ComplianceExportCriteriaHasher criteriaHasher,
            AppendAuditEventService appendService,
            ExportProperties properties,
            EntityManager entityManager,
            Clock clock) {
        this.chainHeadRepository = chainHeadRepository;
        this.eventRepository = eventRepository;
        this.signatureService = signatureService;
        this.criteriaHasher = criteriaHasher;
        this.appendService = appendService;
        this.properties = properties;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ComplianceExportBundle create(String consumerActorId, ComplianceExportScope scope) {
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
            throw new IllegalStateException("Audit chain changed or is incomplete during compliance export");
        }

        var criteria = scope.descriptor();
        var criteriaHash = criteriaHasher.hash(criteria);
        var matchedEventCount = records.stream()
                .filter(record -> AuditExportRecord.FULL.equals(record.kind()))
                .count();
        var generatedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var bundleId = UUID.randomUUID();
        var manifest = new ComplianceExportManifest(
                BUNDLE_VERSION,
                BUNDLE_TYPE,
                bundleId,
                generatedAt,
                criteriaHash,
                criteria,
                new ExportChainHead(head.getLastSequence(), head.getLastHash()),
                matchedEventCount,
                records);
        var signature = signatureService.sign(manifest);

        entityManager.clear();
        var receiptPayload = new LinkedHashMap<String, Object>();
        receiptPayload.put("bundleId", bundleId.toString());
        receiptPayload.put("criteriaHash", criteriaHash);
        receiptPayload.put("scopeType", scope.accountId() == null ? "ACTOR" : "ACCOUNT");
        receiptPayload.put("reportPurpose", scope.reportPurpose().name());
        receiptPayload.put("capturedThroughSequence", manifest.chainHead().sequenceNumber());
        receiptPayload.put("capturedHeadHash", manifest.chainHead().recordHash());
        receiptPayload.put("matchedEventCount", matchedEventCount);
        receiptPayload.put("generatedAt", generatedAt.toString());
        receiptPayload.put("signatureKeyId", signature.keyId());
        appendService.append(new AppendAuditEventCommand(
                RECEIPT_EVENT_TYPE,
                consumerActorId,
                RECEIPT_RESOURCE_TYPE,
                criteriaHash,
                receiptPayload,
                null));

        return new ComplianceExportBundle(manifest, signature);
    }
}
