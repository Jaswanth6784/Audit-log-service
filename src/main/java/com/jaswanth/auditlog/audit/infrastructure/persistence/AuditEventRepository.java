package com.jaswanth.auditlog.audit.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface AuditEventRepository extends
        JpaRepository<AuditEventEntity, Long>,
        JpaSpecificationExecutor<AuditEventEntity> {

    @Query("select event from AuditEventEntity event order by event.sequenceNumber")
    Stream<AuditEventEntity> streamAllInSequenceOrder();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from AuditEventEntity event where event.eventId = :eventId")
    Optional<AuditEventEntity> findByEventIdForUpdate(@Param("eventId") UUID eventId);

    @Query("""
            select event.sequenceNumber
            from AuditEventEntity event
            where event.archivedAt is null and event.recordedAt < :cutoff
            order by event.sequenceNumber
            """)
    List<Long> findUnarchivedSequencesRecordedBefore(
            @Param("cutoff") Instant cutoff,
            org.springframework.data.domain.Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuditEventEntity event
            set event.archivedAt = :archivedAt
            where event.sequenceNumber in :sequences and event.archivedAt is null
            """)
    int archiveBySequenceNumbers(
            @Param("sequences") Collection<Long> sequences,
            @Param("archivedAt") Instant archivedAt);
}
