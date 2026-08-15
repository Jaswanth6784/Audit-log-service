package com.jaswanth.auditlog.compliance.export.application;

import com.jaswanth.auditlog.compliance.export.model.ComplianceExportCriteria;
import com.jaswanth.auditlog.export.infrastructure.CanonicalExportSerializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ComplianceExportCriteriaHasher {

    private static final byte[] DOMAIN = "compliance-export-criteria-v1\n"
            .getBytes(StandardCharsets.UTF_8);

    private final CanonicalExportSerializer serializer;

    public ComplianceExportCriteriaHasher(CanonicalExportSerializer serializer) {
        this.serializer = serializer;
    }

    public String hash(ComplianceExportCriteria criteria) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(DOMAIN);
            digest.update(serializer.serialize(criteria));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
