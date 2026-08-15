package com.jaswanth.auditlog.compliance.export.application;

import com.jaswanth.auditlog.compliance.export.model.ComplianceExportBundle;
import com.jaswanth.auditlog.export.application.ExportRecordChainVerifier;
import com.jaswanth.auditlog.export.application.ExportVerificationResult;
import com.jaswanth.auditlog.export.application.ExportViolation;
import com.jaswanth.auditlog.export.infrastructure.ExportSignatureService;
import org.springframework.stereotype.Service;

@Service
public class VerifyComplianceExportService {

    private final ExportSignatureService signatureService;
    private final ComplianceExportCriteriaHasher criteriaHasher;
    private final ExportRecordChainVerifier chainVerifier;

    public VerifyComplianceExportService(
            ExportSignatureService signatureService,
            ComplianceExportCriteriaHasher criteriaHasher,
            ExportRecordChainVerifier chainVerifier) {
        this.signatureService = signatureService;
        this.criteriaHasher = criteriaHasher;
        this.chainVerifier = chainVerifier;
    }

    public ExportVerificationResult verify(ComplianceExportBundle bundle) {
        if (bundle == null || bundle.manifest() == null) {
            return invalid(ExportViolation.MALFORMED_BUNDLE, "Manifest is required");
        }
        if (!signatureService.verify(bundle.manifest(), bundle.signature())) {
            return invalid(ExportViolation.SIGNATURE_INVALID, "Signature is invalid or untrusted");
        }

        try {
            var manifest = bundle.manifest();
            if (manifest.bundleVersion() != 1
                    || !CreateComplianceExportService.BUNDLE_TYPE.equals(manifest.bundleType())
                    || manifest.bundleId() == null
                    || manifest.generatedAt() == null
                    || manifest.chainHead() == null
                    || manifest.records() == null) {
                return invalid(ExportViolation.MALFORMED_BUNDLE, "Unsupported or incomplete manifest");
            }
            var scope = ComplianceExportScope.from(manifest.criteria());
            if (!criteriaHasher.hash(manifest.criteria()).equals(manifest.criteriaHash())) {
                return invalid(ExportViolation.CRITERIA_HASH_MISMATCH,
                        "Criteria fingerprint cannot be reproduced");
            }
            return chainVerifier.verify(
                    manifest.records(),
                    manifest.chainHead(),
                    manifest.matchedEventCount(),
                    scope::matches);
        } catch (RuntimeException exception) {
            return invalid(ExportViolation.MALFORMED_BUNDLE, "Bundle structure is invalid");
        }
    }

    private ExportVerificationResult invalid(ExportViolation violation, String detail) {
        return ExportVerificationResult.invalid(0, 0, null, violation, detail);
    }
}
