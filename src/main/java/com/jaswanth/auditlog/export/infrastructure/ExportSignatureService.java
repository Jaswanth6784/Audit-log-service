package com.jaswanth.auditlog.export.infrastructure;

import com.jaswanth.auditlog.export.configuration.ExportProperties;
import com.jaswanth.auditlog.export.model.AuditExportManifest;
import com.jaswanth.auditlog.export.model.AuditExportSignature;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class ExportSignatureService {

    public static final String ALGORITHM = "Ed25519";

    private final CanonicalExportSerializer serializer;
    private final ExportProperties properties;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public ExportSignatureService(CanonicalExportSerializer serializer, ExportProperties properties) {
        this.serializer = serializer;
        this.properties = properties;
        try {
            var keyFactory = KeyFactory.getInstance(ALGORITHM);
            privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decode(properties.privateKey())));
            publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(decode(properties.publicKey())));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Audit export signing keys are invalid", exception);
        }
    }

    public AuditExportSignature sign(AuditExportManifest manifest) {
        try {
            var signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            signer.update(serializer.serialize(manifest));
            return new AuditExportSignature(
                    ALGORITHM,
                    properties.keyId(),
                    properties.publicKey(),
                    Base64.getEncoder().encodeToString(signer.sign()));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Audit export could not be signed", exception);
        }
    }

    public boolean verify(AuditExportManifest manifest, AuditExportSignature exportSignature) {
        if (exportSignature == null
                || !ALGORITHM.equals(exportSignature.algorithm())
                || !properties.keyId().equals(exportSignature.keyId())
                || !properties.publicKey().equals(exportSignature.publicKey())
                || exportSignature.value() == null) {
            return false;
        }
        try {
            var verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(serializer.serialize(manifest));
            return verifier.verify(decode(exportSignature.value()));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
