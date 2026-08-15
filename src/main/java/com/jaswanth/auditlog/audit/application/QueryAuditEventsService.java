package com.jaswanth.auditlog.audit.application;

import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventEntity;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueryAuditEventsService {

    private final AuditEventRepository eventRepository;

    public QueryAuditEventsService(AuditEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public AuditEventPage query(AuditEventQuery query) {
        var requestedPlusOne = Math.addExact(query.limit(), 1);
        var candidates = eventRepository.findBy(
                AuditEventSpecifications.forQuery(query),
                fluentQuery -> fluentQuery
                        .sortBy(Sort.by(Sort.Direction.ASC, "sequenceNumber"))
                        .limit(requestedPlusOne)
                        .all());
        var hasMore = candidates.size() > query.limit();
        var selected = hasMore ? candidates.subList(0, query.limit()) : candidates;
        var items = selected.stream().map(this::toView).toList();
        var nextAfterSequence = items.isEmpty()
                ? query.afterSequence()
                : items.getLast().sequenceNumber();
        return new AuditEventPage(items, query.limit(), hasMore, nextAfterSequence);
    }

    private AuditEventView toView(AuditEventEntity entity) {
        return new AuditEventView(
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
}
