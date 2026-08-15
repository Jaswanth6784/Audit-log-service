package com.jaswanth.auditlog.audit.domain;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class AuditHashService {

    public static final short HASH_VERSION = 1;
    public static final short CURRENT_HASH_VERSION = 2;
    public static final String GENESIS_HASH = "0".repeat(64);

    private static final String RECORD_DOMAIN = "audit-record-v1";
    private static final String CURRENT_RECORD_DOMAIN = "audit-record-v2";

    private final CanonicalEventSerializer canonicalEventSerializer;
    private final PayloadCommitmentService payloadCommitmentService;

    public AuditHashService(
            CanonicalEventSerializer canonicalEventSerializer,
            PayloadCommitmentService payloadCommitmentService) {
        this.canonicalEventSerializer = canonicalEventSerializer;
        this.payloadCommitmentService = payloadCommitmentService;
    }

    public AuditHashes calculate(AuditEventContent event, String previousHash) {
        requireSha256Hex(previousHash);
        var canonicalEvent = canonicalEventSerializer.serialize(event);
        return hashes(
                HASH_VERSION,
                RECORD_DOMAIN,
                canonicalEvent,
                previousHash,
                canonicalEvent.payload(),
                java.util.Map.of());
    }

    public AuditHashes calculateCurrent(AuditEventContent event, String previousHash) {
        requireSha256Hex(previousHash);
        var committed = payloadCommitmentService.commit(event.payload());
        var canonicalEvent = canonicalEventSerializer.serialize(event, committed.commitmentTree());
        return hashes(
                CURRENT_HASH_VERSION,
                CURRENT_RECORD_DOMAIN,
                canonicalEvent,
                previousHash,
                committed.payload(),
                committed.proofs());
    }

    public AuditHashes calculateForVerification(
            AuditEventContent event,
            String previousHash,
            short hashVersion,
            java.util.Map<String, Object> payloadProofs) {
        if (hashVersion == HASH_VERSION) {
            return calculate(event, previousHash);
        }
        if (hashVersion == CURRENT_HASH_VERSION) {
            requireSha256Hex(previousHash);
            var commitmentTree = payloadCommitmentService.verifyAndBuildTree(event.payload(), payloadProofs);
            var canonicalEvent = canonicalEventSerializer.serialize(event, commitmentTree);
            return hashes(
                    CURRENT_HASH_VERSION,
                    CURRENT_RECORD_DOMAIN,
                    canonicalEvent,
                    previousHash,
                    event.payload(),
                    payloadProofs);
        }
        throw new IllegalArgumentException("Unsupported hash version " + hashVersion);
    }

    private AuditHashes hashes(
            short hashVersion,
            String recordDomain,
            CanonicalEventSerializer.CanonicalEvent canonicalEvent,
            String previousHash,
            java.util.Map<String, Object> payload,
            java.util.Map<String, Object> payloadProofs) {
        var contentHash = sha256(canonicalEvent.bytes());
        var recordInput = String.join("\n", recordDomain, previousHash, contentHash);
        var recordHash = sha256(recordInput.getBytes(StandardCharsets.UTF_8));
        return new AuditHashes(
                hashVersion,
                contentHash,
                previousHash,
                recordHash,
                payload,
                payloadProofs);
    }

    private void requireSha256Hex(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Previous hash must be 64 lowercase hexadecimal characters");
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
