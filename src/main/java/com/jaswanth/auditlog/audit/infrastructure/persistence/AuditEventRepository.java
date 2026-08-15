package com.jaswanth.auditlog.audit.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.stream.Stream;

public interface AuditEventRepository extends
        JpaRepository<AuditEventEntity, Long>,
        JpaSpecificationExecutor<AuditEventEntity> {

    @Query("select event from AuditEventEntity event order by event.sequenceNumber")
    Stream<AuditEventEntity> streamAllInSequenceOrder();
}
