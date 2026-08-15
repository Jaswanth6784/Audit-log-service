package com.jaswanth.auditlog.audit.infrastructure.persistence;

import com.jaswanth.auditlog.audit.application.AuditEventQuery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;

public final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    public static Specification<AuditEventEntity> forQuery(AuditEventQuery query) {
        var specifications = new ArrayList<Specification<AuditEventEntity>>();
        specifications.add((root, criteriaQuery, builder) ->
                builder.greaterThan(root.get("sequenceNumber"), query.afterSequence()));
        specifications.add((root, criteriaQuery, builder) -> builder.isNull(root.get("archivedAt")));
        addEqual(specifications, "actorId", query.actorId());
        addEqual(specifications, "resourceType", query.resourceType());
        addEqual(specifications, "resourceId", query.resourceId());
        addEqual(specifications, "eventType", query.eventType());
        if (query.from() != null) {
            specifications.add((root, criteriaQuery, builder) ->
                    builder.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
        }
        if (query.to() != null) {
            specifications.add((root, criteriaQuery, builder) ->
                    builder.lessThan(root.get("occurredAt"), query.to()));
        }
        return Specification.allOf(specifications);
    }

    private static void addEqual(
            java.util.List<Specification<AuditEventEntity>> specifications,
            String attribute,
            String value) {
        if (value != null) {
            specifications.add((root, criteriaQuery, builder) -> builder.equal(root.get(attribute), value));
        }
    }
}
