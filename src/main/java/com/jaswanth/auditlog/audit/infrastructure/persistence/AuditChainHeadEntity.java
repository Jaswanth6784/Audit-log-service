package com.jaswanth.auditlog.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "audit_chain_head")
public class AuditChainHeadEntity {

    @Id
    @Column(name = "chain_id")
    private Short chainId;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "last_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String lastHash;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected AuditChainHeadEntity() {
    }

    public void advance(long newSequence, String newHash) {
        if (newSequence <= lastSequence) {
            throw new IllegalArgumentException("Chain sequence must advance monotonically");
        }
        this.lastSequence = newSequence;
        this.lastHash = newHash;
    }

    public long getLastSequence() {
        return lastSequence;
    }

    public String getLastHash() {
        return lastHash;
    }
}
