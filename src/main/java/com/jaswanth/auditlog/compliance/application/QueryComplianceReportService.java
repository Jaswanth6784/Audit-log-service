package com.jaswanth.auditlog.compliance.application;

import com.jaswanth.auditlog.audit.application.AppendAuditEventCommand;
import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
import com.jaswanth.auditlog.audit.application.AuditEventQuery;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditChainHeadRepository;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventEntity;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventSpecifications;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryComplianceReportService {

    public static final String RECEIPT_EVENT_TYPE = "COMPLIANCE_ACCESS_REPORT_VIEWED";
    public static final String RECEIPT_RESOURCE_TYPE = "COMPLIANCE_ACCESS_REPORT";
    private static final short GLOBAL_CHAIN_ID = 1;
    private static final int SCAN_BATCH_SIZE = 500;
    private static final int MAX_SCANNED_EVENTS = 10_000;

    private final AuditEventRepository eventRepository;
    private final AuditChainHeadRepository chainHeadRepository;
    private final AppendAuditEventService appendService;
    private final EntityManager entityManager;

    public QueryComplianceReportService(
            AuditEventRepository eventRepository,
            AuditChainHeadRepository chainHeadRepository,
            AppendAuditEventService appendService,
            EntityManager entityManager) {
        this.eventRepository = eventRepository;
        this.chainHeadRepository = chainHeadRepository;
        this.appendService = appendService;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ComplianceReportPage query(String consumerActorId, ComplianceReportQuery query) {
        var capturedThroughSequence = chainHeadRepository.findLastSequenceByChainId(GLOBAL_CHAIN_ID)
                .orElseThrow(() -> new IllegalStateException("Global audit chain head is missing"));
        var matches = new ArrayList<AuditEventEntity>(query.limit() + 1);
        var scanCursor = query.afterSequence();
        var scannedCount = 0;
        var exhausted = false;

        while (matches.size() <= query.limit() && !exhausted) {
            if (scannedCount >= MAX_SCANNED_EVENTS) {
                throw new ComplianceReportScanLimitException(
                        "Report criteria matched too many candidate events; narrow the time range");
            }
            var fetchSize = Math.min(SCAN_BATCH_SIZE, MAX_SCANNED_EVENTS - scannedCount);
            var candidates = candidates(query, scanCursor, capturedThroughSequence, fetchSize);
            if (candidates.isEmpty()) {
                exhausted = true;
                continue;
            }
            for (var candidate : candidates) {
                scanCursor = candidate.getSequenceNumber();
                scannedCount++;
                if (matchesPayload(candidate.getPayload(), query)) {
                    matches.add(candidate);
                    if (matches.size() > query.limit()) {
                        break;
                    }
                }
            }
            exhausted = matches.size() <= query.limit() && candidates.size() < fetchSize;
        }

        var hasMore = matches.size() > query.limit();
        var selected = hasMore ? matches.subList(0, query.limit()) : matches;
        var entries = selected.stream().map(this::toEntry).toList();
        var nextAfterSequence = hasMore
                ? selected.getLast().getSequenceNumber()
                : scanCursor;
        var criteriaHash = criteriaHash(query);
        // JSON-backed maps are mutable to Hibernate. Detach the read snapshot so appending
        // the receipt cannot trigger dirty-check updates against immutable evidence rows.
        entityManager.clear();
        var receipt = appendService.append(new AppendAuditEventCommand(
                RECEIPT_EVENT_TYPE,
                consumerActorId,
                RECEIPT_RESOURCE_TYPE,
                criteriaHash,
                receiptPayload(query, criteriaHash, entries, hasMore, nextAfterSequence, capturedThroughSequence),
                null));

        return new ComplianceReportPage(
                entries,
                query.limit(),
                hasMore,
                nextAfterSequence,
                capturedThroughSequence,
                criteriaHash,
                receipt.eventId());
    }

    private List<AuditEventEntity> candidates(
            ComplianceReportQuery query,
            long afterSequence,
            long capturedThroughSequence,
            int fetchSize) {
        var baseQuery = new AuditEventQuery(
                query.actorId(),
                RecordComplianceAccessService.RESOURCE_TYPE,
                query.accountId(),
                RecordComplianceAccessService.EVENT_TYPE,
                query.from(),
                query.to(),
                afterSequence,
                fetchSize);
        Specification<AuditEventEntity> capturedBoundary = (root, criteriaQuery, builder) ->
                builder.lessThanOrEqualTo(root.get("sequenceNumber"), capturedThroughSequence);
        return eventRepository.findBy(
                AuditEventSpecifications.forQuery(baseQuery).and(capturedBoundary),
                fluentQuery -> fluentQuery
                        .sortBy(Sort.by(Sort.Direction.ASC, "sequenceNumber"))
                        .limit(fetchSize)
                        .all());
    }

    private boolean matchesPayload(Map<String, Object> payload, ComplianceReportQuery query) {
        return matches(payload.get("action"), query.action())
                && matches(payload.get("outcome"), query.outcome())
                && matches(payload.get("sourceSystem"), query.sourceSystem())
                && matchesCategory(payload.get("dataCategories"), query);
    }

    private boolean matches(Object actual, Enum<?> expected) {
        return expected == null || expected.name().equals(actual);
    }

    private boolean matches(Object actual, String expected) {
        return expected == null || expected.equals(actual);
    }

    private boolean matchesCategory(Object actual, ComplianceReportQuery query) {
        if (query.dataCategory() == null) {
            return true;
        }
        return actual instanceof List<?> values && values.contains(query.dataCategory().name());
    }

    private ComplianceReportEntry toEntry(AuditEventEntity entity) {
        var payload = entity.getPayload();
        return new ComplianceReportEntry(
                entity.getSequenceNumber(),
                entity.getEventId(),
                entity.getActorId(),
                entity.getResourceId(),
                stringValue(payload.get("action")),
                stringValue(payload.get("outcome")),
                stringList(payload.get("dataCategories")),
                stringValue(payload.get("purposeCode")),
                stringValue(payload.get("sourceSystem")),
                stringValue(payload.get("correlationId")),
                stringValue(payload.get("reasonCode")),
                entity.getOccurredAt(),
                entity.getRecordedAt(),
                entity.isRedacted(),
                entity.getHashVersion(),
                entity.getPreviousHash(),
                entity.getRecordHash());
    }

    private Map<String, Object> receiptPayload(
            ComplianceReportQuery query,
            String criteriaHash,
            List<ComplianceReportEntry> entries,
            boolean hasMore,
            long nextAfterSequence,
            long capturedThroughSequence) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("criteriaHash", criteriaHash);
        payload.put("scopeType", query.scopeType());
        payload.put("reportPurpose", query.reportPurpose().name());
        payload.put("capturedThroughSequence", capturedThroughSequence);
        payload.put("requestedAfterSequence", query.afterSequence());
        payload.put("requestedLimit", query.limit());
        payload.put("returnedCount", entries.size());
        payload.put("hasMore", hasMore);
        payload.put("nextAfterSequence", nextAfterSequence);
        if (!entries.isEmpty()) {
            payload.put("firstReturnedSequence", entries.getFirst().sequenceNumber());
            payload.put("lastReturnedSequence", entries.getLast().sequenceNumber());
        }
        return payload;
    }

    private String criteriaHash(ComplianceReportQuery query) {
        var values = List.of(
                query.scopeType(), query.scopeValue(), query.from().toString(), query.to().toString(),
                query.reportPurpose().name(),
                name(query.action()), name(query.outcome()), value(query.sourceSystem()),
                name(query.dataCategory()), Long.toString(query.afterSequence()), Integer.toString(query.limit()));
        var framed = new StringBuilder("compliance-report-criteria-v1");
        values.forEach(value -> framed.append('\n').append(value.length()).append(':').append(value));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(framed.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String name(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
}
