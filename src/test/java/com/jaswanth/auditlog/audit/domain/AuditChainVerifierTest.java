package com.jaswanth.auditlog.audit.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditChainVerifierTest {

    private AuditHashService hashService;
    private AuditChainVerifier verifier;

    @BeforeEach
    void setUp() {
        hashService = new AuditHashService(new CanonicalEventSerializer(new ObjectMapper()));
        verifier = new AuditChainVerifier(hashService);
    }

    @Test
    void acceptsEmptyAndCompleteChains() {
        var empty = verifier.verify(
                List.<StoredAuditEvent>of().iterator(),
                0,
                AuditHashService.GENESIS_HASH);
        assertThat(empty.valid()).isTrue();
        assertThat(empty.verifiedEventCount()).isZero();
        assertThat(empty.violationType()).isNull();

        var first = stored(1, content("one"), AuditHashService.GENESIS_HASH);
        var second = stored(2, content("two"), first.recordHash());
        var complete = verifier.verify(List.of(first, second).iterator(), 2, second.recordHash());

        assertThat(complete.valid()).isTrue();
        assertThat(complete.verifiedEventCount()).isEqualTo(2);
        assertThat(complete.chainHeadSequence()).isEqualTo(2);
        assertThat(complete.detail()).isEqualTo("Audit chain is valid");
    }

    @Test
    void classifiesTheFirstEventLevelViolation() {
        var first = stored(1, content("one"), AuditHashService.GENESIS_HASH);
        var second = stored(2, content("two"), first.recordHash());

        assertViolation(
                List.of(first, copy(3, second.hashVersion(), second.content(),
                        second.contentHash(), second.previousHash(), second.recordHash())),
                AuditChainViolation.SEQUENCE_GAP,
                3,
                1);
        assertViolation(
                List.of(copy(1, (short) 99, first.content(),
                        first.contentHash(), first.previousHash(), first.recordHash())),
                AuditChainViolation.UNSUPPORTED_HASH_VERSION,
                1,
                0);
        assertViolation(
                List.of(first, copy(2, second.hashVersion(), second.content(),
                        second.contentHash(), "f".repeat(64), second.recordHash())),
                AuditChainViolation.PREVIOUS_HASH_MISMATCH,
                2,
                1);
        assertViolation(
                List.of(copy(1, first.hashVersion(), content("tampered"),
                        first.contentHash(), first.previousHash(), first.recordHash())),
                AuditChainViolation.CONTENT_HASH_MISMATCH,
                1,
                0);
        assertViolation(
                List.of(copy(1, first.hashVersion(), first.content(),
                        first.contentHash(), first.previousHash(), "f".repeat(64))),
                AuditChainViolation.RECORD_HASH_MISMATCH,
                1,
                0);
    }

    @Test
    void classifiesChainHeadViolationsAfterAllEventsVerify() {
        var first = stored(1, content("one"), AuditHashService.GENESIS_HASH);

        var sequenceMismatch = verifier.verify(List.of(first).iterator(), 2, first.recordHash());
        assertThat(sequenceMismatch.violationType())
                .isEqualTo(AuditChainViolation.CHAIN_HEAD_SEQUENCE_MISMATCH);
        assertThat(sequenceMismatch.verifiedEventCount()).isEqualTo(1);

        var hashMismatch = verifier.verify(List.of(first).iterator(), 1, "f".repeat(64));
        assertThat(hashMismatch.violationType()).isEqualTo(AuditChainViolation.CHAIN_HEAD_HASH_MISMATCH);
        assertThat(hashMismatch.firstInvalidSequence()).isEqualTo(1);
    }

    private void assertViolation(
            List<StoredAuditEvent> events,
            AuditChainViolation expectedViolation,
            long expectedSequence,
            long expectedVerifiedCount) {
        var result = verifier.verify(events.iterator(), events.getLast().sequenceNumber(), events.getLast().recordHash());
        assertThat(result.valid()).isFalse();
        assertThat(result.violationType()).isEqualTo(expectedViolation);
        assertThat(result.firstInvalidSequence()).isEqualTo(expectedSequence);
        assertThat(result.verifiedEventCount()).isEqualTo(expectedVerifiedCount);
        assertThat(result.detail()).isNotBlank();
    }

    private StoredAuditEvent stored(long sequence, AuditEventContent content, String previousHash) {
        var hashes = hashService.calculate(content, previousHash);
        return new StoredAuditEvent(
                sequence,
                hashes.hashVersion(),
                content,
                hashes.contentHash(),
                hashes.previousHash(),
                hashes.recordHash());
    }

    private StoredAuditEvent copy(
            long sequence,
            short hashVersion,
            AuditEventContent content,
            String contentHash,
            String previousHash,
            String recordHash) {
        return new StoredAuditEvent(
                sequence,
                hashVersion,
                content,
                contentHash,
                previousHash,
                recordHash);
    }

    private AuditEventContent content(String value) {
        return new AuditEventContent(
                "RECORD_UPDATED",
                "actor-7",
                "ACCOUNT",
                "resource-9",
                Map.of("value", value),
                Instant.parse("2026-08-15T10:15:30.123456Z"));
    }
}
