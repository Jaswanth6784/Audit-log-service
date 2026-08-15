package com.jaswanth.auditlog.audit.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadCommitmentServiceTest {

    private PayloadCommitmentService commitmentService;

    @BeforeEach
    void setUp() {
        commitmentService = new PayloadCommitmentService(new CanonicalEventSerializer(new ObjectMapper()));
    }

    @Test
    void commitsNestedObjectsArraysAndEscapedJsonPointerTokens() {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("customer", Map.of("name", "Jaswanth", "active", true));
        payload.put("items", new ArrayList<>(java.util.List.of("one", 2)));
        payload.put("a/b~c", null);

        var committed = commitmentService.commit(payload);
        var rebuilt = commitmentService.verifyAndBuildTree(committed.payload(), committed.proofs());

        assertThat(rebuilt).isEqualTo(committed.commitmentTree());
        assertThat(committed.proofs().keySet())
                .containsExactlyInAnyOrder(
                        "/a~1b~0c", "/customer/active", "/customer/name", "/items/0", "/items/1");
    }

    @Test
    void removesAValueAndSaltWhilePreservingTheCommitmentTree() {
        var committed = commitmentService.commit(Map.of(
                "customer", Map.of("name", "Jaswanth", "ssn", "111-22-3333")));

        var redacted = commitmentService.redact(
                committed.payload(),
                committed.proofs(),
                java.util.List.of("/customer/ssn"));

        assertThat(commitmentService.verifyAndBuildTree(redacted.payload(), redacted.proofs()))
                .isEqualTo(committed.commitmentTree());
        var customer = asMap(redacted.payload().get("customer"));
        assertThat(asMap(customer.get("ssn"))).containsOnlyKeys("$redacted");
        assertThat(asMap(redacted.proofs().get("/customer/ssn")).get("salt")).isNull();
        assertThat(customer.get("name")).isEqualTo("Jaswanth");
    }

    @Test
    void rejectsTamperingUnknownPathsAndRepeatedRedaction() {
        var committed = commitmentService.commit(Map.of("name", "original", "secret", "remove-me"));
        var tampered = new LinkedHashMap<>(committed.payload());
        tampered.put("name", "attacker");

        assertThatThrownBy(() -> commitmentService.verifyAndBuildTree(tampered, committed.proofs()))
                .isInstanceOf(InvalidPayloadProofException.class)
                .hasMessageContaining("commitment mismatch");
        assertThatThrownBy(() -> commitmentService.redact(
                committed.payload(), committed.proofs(), java.util.List.of("/missing")))
                .isInstanceOf(InvalidPayloadProofException.class)
                .hasMessageContaining("Unknown payload leaf path");

        var redacted = commitmentService.redact(
                committed.payload(), committed.proofs(), java.util.List.of("/secret"));
        assertThatThrownBy(() -> commitmentService.redact(
                redacted.payload(), redacted.proofs(), java.util.List.of("/secret")))
                .isInstanceOf(InvalidPayloadProofException.class)
                .hasMessageContaining("already redacted");
    }

    @Test
    void rejectsMalformedMissingAndInconsistentProofMaterial() {
        var committed = commitmentService.commit(Map.of("name", "Jaswanth", "secret", "value"));

        assertThatThrownBy(() -> commitmentService.verifyAndBuildTree(committed.payload(), null))
                .isInstanceOf(InvalidPayloadProofException.class)
                .hasMessageContaining("proofs are missing");

        var extraProof = new LinkedHashMap<>(committed.proofs());
        extraProof.put("/removed", new LinkedHashMap<>(asMap(committed.proofs().get("/name"))));
        assertThatThrownBy(() -> commitmentService.verifyAndBuildTree(committed.payload(), extraProof))
                .isInstanceOf(InvalidPayloadProofException.class)
                .hasMessageContaining("paths do not match");

        var invalidSaltProofs = copyProofs(committed.proofs());
        asMap(invalidSaltProofs.get("/name")).put("salt", "invalid");
        assertThatThrownBy(() -> commitmentService.verifyAndBuildTree(committed.payload(), invalidSaltProofs))
                .isInstanceOf(InvalidPayloadProofException.class)
                .hasMessageContaining("Invalid salt");

        var invalidCommitmentProofs = copyProofs(committed.proofs());
        asMap(invalidCommitmentProofs.get("/name")).put("commitment", "invalid");
        assertThatThrownBy(() -> commitmentService.verifyAndBuildTree(committed.payload(), invalidCommitmentProofs))
                .isInstanceOf(InvalidPayloadProofException.class)
                .hasMessageContaining("Invalid commitment");

        var redacted = commitmentService.redact(
                committed.payload(), committed.proofs(), java.util.List.of("/secret"));
        var badMarkerPayload = new LinkedHashMap<>(redacted.payload());
        badMarkerPayload.put("secret", Map.of("$redacted", "f".repeat(64)));
        assertThatThrownBy(() -> commitmentService.verifyAndBuildTree(badMarkerPayload, redacted.proofs()))
                .isInstanceOf(InvalidPayloadProofException.class)
                .hasMessageContaining("Invalid redaction marker");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> copyProofs(Map<String, Object> proofs) {
        var result = new LinkedHashMap<String, Object>();
        proofs.forEach((path, proof) -> result.put(path, new LinkedHashMap<>(asMap(proof))));
        return result;
    }
}
