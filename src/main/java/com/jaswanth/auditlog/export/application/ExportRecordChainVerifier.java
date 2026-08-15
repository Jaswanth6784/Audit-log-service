package com.jaswanth.auditlog.export.application;

import com.jaswanth.auditlog.audit.domain.AuditEventContent;
import com.jaswanth.auditlog.audit.domain.AuditHashService;
import com.jaswanth.auditlog.audit.domain.InvalidPayloadProofException;
import com.jaswanth.auditlog.export.model.AuditExportRecord;
import com.jaswanth.auditlog.export.model.ExportChainHead;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Component
public class ExportRecordChainVerifier {

    private final AuditHashService hashService;

    public ExportRecordChainVerifier(AuditHashService hashService) {
        this.hashService = hashService;
    }

    public ExportVerificationResult verify(
            List<AuditExportRecord> sourceRecords,
            ExportChainHead chainHead,
            long declaredMatchCount,
            Predicate<AuditExportRecord> fullRecordScope) {
        var records = new ArrayList<>(sourceRecords);
        var expectedPreviousHash = AuditHashService.GENESIS_HASH;
        long checked = 0;
        long full = 0;

        for (var record : records) {
            var sequence = record == null ? checked + 1 : record.sequenceNumber();
            if (record == null) {
                return invalid(checked, full, sequence, ExportViolation.MALFORMED_BUNDLE, "Record is required");
            }
            if (record.sequenceNumber() != checked + 1) {
                return invalid(checked, full, sequence, ExportViolation.SEQUENCE_GAP,
                        "Expected sequence " + (checked + 1));
            }
            if (!expectedPreviousHash.equals(record.previousHash())) {
                return invalid(checked, full, sequence, ExportViolation.PREVIOUS_HASH_MISMATCH,
                        "Previous hash does not link to the preceding record");
            }

            if (AuditExportRecord.FULL.equals(record.kind())) {
                var contentResult = verifyFullRecord(record, fullRecordScope, checked, full);
                if (contentResult != null) {
                    return contentResult;
                }
                full++;
            } else if (!AuditExportRecord.BRIDGE.equals(record.kind())) {
                return invalid(checked, full, sequence, ExportViolation.UNSUPPORTED_RECORD_KIND,
                        "Record kind must be FULL or BRIDGE");
            }

            final String expectedRecordHash;
            try {
                expectedRecordHash = hashService.calculateRecordHash(
                        record.hashVersion(), record.previousHash(), record.contentHash());
            } catch (IllegalArgumentException exception) {
                return invalid(checked, full, sequence, ExportViolation.MALFORMED_BUNDLE,
                        "Hash metadata is invalid");
            }
            if (!expectedRecordHash.equals(record.recordHash())) {
                return invalid(checked, full, sequence, ExportViolation.RECORD_HASH_MISMATCH,
                        "Record hash cannot be reproduced");
            }
            expectedPreviousHash = record.recordHash();
            checked++;
        }

        if (chainHead.sequenceNumber() != checked
                || !expectedPreviousHash.equals(chainHead.recordHash())) {
            return invalid(checked, full, null, ExportViolation.CHAIN_HEAD_MISMATCH,
                    "Manifest chain head does not match the exported records");
        }
        if (declaredMatchCount != full) {
            return invalid(checked, full, null, ExportViolation.MATCH_COUNT_MISMATCH,
                    "Matched event count does not match FULL records");
        }
        return ExportVerificationResult.valid(checked, full);
    }

    private ExportVerificationResult verifyFullRecord(
            AuditExportRecord record,
            Predicate<AuditExportRecord> fullRecordScope,
            long checked,
            long full) {
        if (!isComplete(record)) {
            return invalid(checked, full, record.sequenceNumber(), ExportViolation.MALFORMED_BUNDLE,
                    "FULL record content is incomplete");
        }
        if (!fullRecordScope.test(record)) {
            return invalid(checked, full, record.sequenceNumber(), ExportViolation.SCOPE_MISMATCH,
                    "FULL record is outside the declared scope or criteria");
        }
        try {
            var hashes = hashService.calculateForVerification(
                    new AuditEventContent(
                            record.eventType(), record.actorId(), record.resourceType(), record.resourceId(),
                            record.payload(), record.timestamp()),
                    record.previousHash(),
                    record.hashVersion(),
                    record.payloadProofs());
            if (!hashes.contentHash().equals(record.contentHash())) {
                return invalid(checked, full, record.sequenceNumber(), ExportViolation.CONTENT_HASH_MISMATCH,
                        "Disclosed event content does not match its commitment");
            }
        } catch (InvalidPayloadProofException exception) {
            return invalid(checked, full, record.sequenceNumber(), ExportViolation.PAYLOAD_PROOF_MISMATCH,
                    "Payload commitment proof is invalid");
        } catch (IllegalArgumentException exception) {
            return invalid(checked, full, record.sequenceNumber(), ExportViolation.MALFORMED_BUNDLE,
                    "FULL record hash input is invalid");
        }
        return null;
    }

    private boolean isComplete(AuditExportRecord record) {
        return record.eventId() != null
                && record.eventType() != null
                && record.actorId() != null
                && record.resourceType() != null
                && record.resourceId() != null
                && record.payload() != null
                && (record.hashVersion() == AuditHashService.HASH_VERSION || record.payloadProofs() != null)
                && record.timestamp() != null
                && record.recordedAt() != null;
    }

    private ExportVerificationResult invalid(
            long checked,
            long full,
            Long sequence,
            ExportViolation violation,
            String detail) {
        return ExportVerificationResult.invalid(checked, full, sequence, violation, detail);
    }
}
