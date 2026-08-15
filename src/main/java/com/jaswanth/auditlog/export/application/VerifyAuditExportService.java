package com.jaswanth.auditlog.export.application;

import com.jaswanth.auditlog.audit.domain.AuditEventContent;
import com.jaswanth.auditlog.audit.domain.AuditHashService;
import com.jaswanth.auditlog.audit.domain.InvalidPayloadProofException;
import com.jaswanth.auditlog.export.infrastructure.ExportSignatureService;
import com.jaswanth.auditlog.export.model.AuditExportBundle;
import com.jaswanth.auditlog.export.model.AuditExportRecord;
import com.jaswanth.auditlog.export.model.ExportScopeDescriptor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class VerifyAuditExportService {

    private final ExportSignatureService signatureService;
    private final AuditHashService hashService;

    public VerifyAuditExportService(ExportSignatureService signatureService, AuditHashService hashService) {
        this.signatureService = signatureService;
        this.hashService = hashService;
    }

    public ExportVerificationResult verify(AuditExportBundle bundle) {
        if (bundle == null || bundle.manifest() == null) {
            return invalid(0, 0, null, ExportViolation.MALFORMED_BUNDLE, "Manifest is required");
        }
        if (!signatureService.verify(bundle.manifest(), bundle.signature())) {
            return invalid(0, 0, null, ExportViolation.SIGNATURE_INVALID, "Signature is invalid or untrusted");
        }

        try {
            return verifySignedManifest(bundle);
        } catch (RuntimeException exception) {
            return invalid(0, 0, null, ExportViolation.MALFORMED_BUNDLE, "Bundle structure is invalid");
        }
    }

    private ExportVerificationResult verifySignedManifest(AuditExportBundle bundle) {
        var manifest = bundle.manifest();
        if (manifest.bundleVersion() != 1 || manifest.scope() == null
                || manifest.chainHead() == null || manifest.records() == null) {
            return invalid(0, 0, null, ExportViolation.MALFORMED_BUNDLE, "Unsupported or incomplete manifest");
        }

        var records = new ArrayList<>(manifest.records());
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
                var contentResult = verifyFullRecord(record, manifest.scope(), checked, full);
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

        if (manifest.chainHead().sequenceNumber() != checked
                || !expectedPreviousHash.equals(manifest.chainHead().recordHash())) {
            return invalid(checked, full, null, ExportViolation.CHAIN_HEAD_MISMATCH,
                    "Manifest chain head does not match the exported records");
        }
        if (manifest.matchedEventCount() != full) {
            return invalid(checked, full, null, ExportViolation.MATCH_COUNT_MISMATCH,
                    "Matched event count does not match FULL records");
        }
        return ExportVerificationResult.valid(checked, full);
    }

    private ExportVerificationResult verifyFullRecord(
            AuditExportRecord record,
            ExportScopeDescriptor scope,
            long checked,
            long full) {
        if (!isComplete(record)) {
            return invalid(checked, full, record.sequenceNumber(), ExportViolation.MALFORMED_BUNDLE,
                    "FULL record content is incomplete");
        }
        if (!matches(scope, record)) {
            return invalid(checked, full, record.sequenceNumber(), ExportViolation.SCOPE_MISMATCH,
                    "FULL record is outside the declared scope");
        }
        try {
            var hashes = hashService.calculateForVerification(
                    new AuditEventContent(
                            record.eventType(),
                            record.actorId(),
                            record.resourceType(),
                            record.resourceId(),
                            record.payload(),
                            record.timestamp()),
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

    private boolean matches(ExportScopeDescriptor scope, AuditExportRecord record) {
        if ("ACTOR".equals(scope.type())) {
            return scope.actorId() != null
                    && scope.resourceType() == null
                    && scope.resourceId() == null
                    && scope.actorId().equals(record.actorId());
        }
        if ("RESOURCE".equals(scope.type())) {
            return scope.actorId() == null
                    && scope.resourceType() != null
                    && scope.resourceId() != null
                    && scope.resourceType().equals(record.resourceType())
                    && scope.resourceId().equals(record.resourceId());
        }
        return false;
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
