package com.jaswanth.auditlog.audit.domain;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class AuditHashService {

    public static final short HASH_VERSION = 1;
    public static final String GENESIS_HASH = "0".repeat(64);

    private static final String RECORD_DOMAIN = "audit-record-v1";

    private final CanonicalEventSerializer canonicalEventSerializer;

    public AuditHashService(CanonicalEventSerializer canonicalEventSerializer) {
        this.canonicalEventSerializer = canonicalEventSerializer;
    }

    public AuditHashes calculate(AuditEventContent event, String previousHash) {
        requireSha256Hex(previousHash);
        var canonicalEvent = canonicalEventSerializer.serialize(event);
        var contentHash = sha256(canonicalEvent.bytes());
        var recordInput = String.join("\n", RECORD_DOMAIN, previousHash, contentHash);
        var recordHash = sha256(recordInput.getBytes(StandardCharsets.UTF_8));
        return new AuditHashes(HASH_VERSION, contentHash, previousHash, recordHash, canonicalEvent.payload());
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
