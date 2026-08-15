package com.jaswanth.auditlog.audit.infrastructure.persistence;

import com.jaswanth.auditlog.audit.domain.AuditEventContent;
import com.jaswanth.auditlog.audit.domain.AuditHashes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence_number")
    private Long sequenceNumber;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 255)
    private String actorId;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false, length = 255)
    private String resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private Map<String, Object> payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_proofs", columnDefinition = "json")
    private Map<String, Object> payloadProofs;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "hash_version", nullable = false, updatable = false)
    private short hashVersion;

    @Column(name = "content_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
    private String contentHash;

    @Column(name = "previous_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
    private String previousHash;

    @Column(name = "record_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
    private String recordHash;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "redacted", nullable = false)
    private boolean redacted;

    @Column(name = "redacted_at")
    private Instant redactedAt;

    protected AuditEventEntity() {
    }

    private AuditEventEntity(UUID eventId, AuditEventContent content, Instant recordedAt, AuditHashes hashes) {
        this.eventId = eventId;
        this.eventType = content.eventType();
        this.actorId = content.actorId();
        this.resourceType = content.resourceType();
        this.resourceId = content.resourceId();
        this.payload = hashes.canonicalPayload();
        this.payloadProofs = hashes.payloadProofs();
        this.occurredAt = content.timestamp();
        this.recordedAt = recordedAt;
        this.hashVersion = hashes.hashVersion();
        this.contentHash = hashes.contentHash();
        this.previousHash = hashes.previousHash();
        this.recordHash = hashes.recordHash();
    }

    public static AuditEventEntity create(
            UUID eventId,
            AuditEventContent content,
            Instant recordedAt,
            AuditHashes hashes) {
        return new AuditEventEntity(eventId, content, recordedAt, hashes);
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Map<String, Object> getPayloadProofs() {
        return payloadProofs;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public short getHashVersion() {
        return hashVersion;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getRecordHash() {
        return recordHash;
    }

    public boolean isRedacted() {
        return redacted;
    }

    public Instant getRedactedAt() {
        return redactedAt;
    }

    public void redact(Map<String, Object> redactedPayload, Map<String, Object> updatedProofs, Instant timestamp) {
        this.payload = redactedPayload;
        this.payloadProofs = updatedProofs;
        this.redacted = true;
        this.redactedAt = timestamp;
    }
}
