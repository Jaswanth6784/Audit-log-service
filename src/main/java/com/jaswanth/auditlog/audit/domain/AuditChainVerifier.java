package com.jaswanth.auditlog.audit.domain;

import org.springframework.stereotype.Component;

import java.util.Iterator;

@Component
public class AuditChainVerifier {

    private final AuditHashService hashService;

    public AuditChainVerifier(AuditHashService hashService) {
        this.hashService = hashService;
    }

    public AuditChainVerification verify(
            Iterator<StoredAuditEvent> events,
            long chainHeadSequence,
            String chainHeadHash) {
        long expectedSequence = 1;
        long verifiedCount = 0;
        var expectedPreviousHash = AuditHashService.GENESIS_HASH;

        while (events.hasNext()) {
            var event = events.next();
            if (event.sequenceNumber() != expectedSequence) {
                return invalid(
                        verifiedCount,
                        chainHeadSequence,
                        event.sequenceNumber(),
                        AuditChainViolation.SEQUENCE_GAP,
                        "Expected sequence " + expectedSequence + " but found " + event.sequenceNumber());
            }
            if (event.hashVersion() != AuditHashService.HASH_VERSION) {
                return invalid(
                        verifiedCount,
                        chainHeadSequence,
                        event.sequenceNumber(),
                        AuditChainViolation.UNSUPPORTED_HASH_VERSION,
                        "Unsupported hash version " + event.hashVersion());
            }
            if (!expectedPreviousHash.equals(event.previousHash())) {
                return invalid(
                        verifiedCount,
                        chainHeadSequence,
                        event.sequenceNumber(),
                        AuditChainViolation.PREVIOUS_HASH_MISMATCH,
                        "Stored previous hash does not match the preceding record hash");
            }

            var expectedHashes = hashService.calculate(event.content(), expectedPreviousHash);
            if (!expectedHashes.contentHash().equals(event.contentHash())) {
                return invalid(
                        verifiedCount,
                        chainHeadSequence,
                        event.sequenceNumber(),
                        AuditChainViolation.CONTENT_HASH_MISMATCH,
                        "Stored content hash does not match the canonical event content");
            }
            if (!expectedHashes.recordHash().equals(event.recordHash())) {
                return invalid(
                        verifiedCount,
                        chainHeadSequence,
                        event.sequenceNumber(),
                        AuditChainViolation.RECORD_HASH_MISMATCH,
                        "Stored record hash does not match the recomputed record hash");
            }

            verifiedCount++;
            expectedSequence++;
            expectedPreviousHash = event.recordHash();
        }

        var lastSequence = expectedSequence - 1;
        if (chainHeadSequence != lastSequence) {
            return invalid(
                    verifiedCount,
                    chainHeadSequence,
                    chainHeadSequence,
                    AuditChainViolation.CHAIN_HEAD_SEQUENCE_MISMATCH,
                    "Chain head sequence does not match the final stored event");
        }
        if (!expectedPreviousHash.equals(chainHeadHash)) {
            return invalid(
                    verifiedCount,
                    chainHeadSequence,
                    chainHeadSequence,
                    AuditChainViolation.CHAIN_HEAD_HASH_MISMATCH,
                    "Chain head hash does not match the final stored event hash");
        }
        return AuditChainVerification.valid(verifiedCount, chainHeadSequence);
    }

    private AuditChainVerification invalid(
            long verifiedCount,
            long chainHeadSequence,
            long firstInvalidSequence,
            AuditChainViolation violation,
            String detail) {
        return AuditChainVerification.invalid(
                verifiedCount,
                chainHeadSequence,
                firstInvalidSequence,
                violation,
                detail);
    }
}
