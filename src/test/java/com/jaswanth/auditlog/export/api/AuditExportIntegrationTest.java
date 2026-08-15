package com.jaswanth.auditlog.export.api;

import com.jaswanth.auditlog.audit.application.AppendAuditEventCommand;
import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
import com.jaswanth.auditlog.export.application.CreateAuditExportService;
import com.jaswanth.auditlog.export.application.ExportScope;
import com.jaswanth.auditlog.export.application.VerifyAuditExportService;
import com.jaswanth.auditlog.export.infrastructure.ExportSignatureService;
import com.jaswanth.auditlog.export.model.AuditExportBundle;
import com.jaswanth.auditlog.export.model.AuditExportManifest;
import com.jaswanth.auditlog.export.model.AuditExportRecord;
import com.jaswanth.auditlog.export.model.AuditExportSignature;
import com.jaswanth.auditlog.export.model.ExportChainHead;
import com.jaswanth.auditlog.export.model.ExportScopeDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url="
                + "jdbc:h2:mem:audit-export-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("h2")
class AuditExportIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AppendAuditEventService appendService;

    @Autowired
    private CreateAuditExportService createService;

    @Autowired
    private VerifyAuditExportService verificationService;

    @Autowired
    private ExportSignatureService signatureService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.execute("ALTER TABLE audit_event ALTER COLUMN sequence_number RESTART WITH 1");
        jdbcTemplate.update(
                "UPDATE audit_chain_head SET last_sequence = 0, last_hash = ?, lock_version = 0 WHERE chain_id = 1",
                "0".repeat(64));
    }

    @Test
    void exportsOnlyScopedContentWithBridgesAndVerifiesOverHttp() throws Exception {
        append("USER_LOGIN", "actor-A", "resource-1", "visible-one");
        append("RECORD_UPDATED", "actor-B", "resource-2", "unrelated-secret");
        append("PERMISSION_GRANTED", "actor-A", "resource-1", "visible-two");
        append("USER_LOGOUT", "actor-B", "resource-2", "another-secret");

        var exportResponse = get("/audit/exports?actorId=" + encode("actor-A"));

        assertThat(exportResponse.statusCode()).isEqualTo(200);
        assertThat(exportResponse.headers().firstValue("Content-Disposition").orElseThrow())
                .startsWith("attachment; filename=\"audit-export-");
        assertThat(exportResponse.body())
                .doesNotContain("actor-B", "unrelated-secret", "another-secret");

        var bundleJson = objectMapper.readTree(exportResponse.body());
        assertThat(bundleJson.get("manifest").get("matchedEventCount").asLong()).isEqualTo(2);
        assertThat(bundleJson.get("manifest").get("records").size()).isEqualTo(4);
        assertThat(kinds(bundleJson)).containsExactly("FULL", "BRIDGE", "FULL", "BRIDGE");

        var verificationResponse = post("/audit/exports/verification", exportResponse.body());
        assertThat(verificationResponse.statusCode()).isEqualTo(200);
        var verification = objectMapper.readTree(verificationResponse.body());
        assertThat(verification.get("valid").asBoolean()).isTrue();
        assertThat(verification.get("checkedRecordCount").asLong()).isEqualTo(4);
        assertThat(verification.get("fullRecordCount").asLong()).isEqualTo(2);

        var tampered = exportResponse.body().replace("visible-one", "changed-value");
        var tamperedResponse = post("/audit/exports/verification", tampered);
        assertThat(objectMapper.readTree(tamperedResponse.body()).get("violationType").asString())
                .isEqualTo("SIGNATURE_INVALID");

        var resourceResponse = get(
                "/audit/exports?resourceType=" + encode("ACCOUNT") + "&resourceId=" + encode("resource-2"));
        assertThat(resourceResponse.statusCode()).isEqualTo(200);
        assertThat(kinds(objectMapper.readTree(resourceResponse.body())))
                .containsExactly("BRIDGE", "FULL", "BRIDGE", "FULL");
    }

    @Test
    void rejectsInvalidHttpScopes() throws Exception {
        assertThat(get("/audit/exports").statusCode()).isEqualTo(400);
        assertThat(get("/audit/exports?resourceType=ACCOUNT").statusCode()).isEqualTo(400);
        assertThat(get("/audit/exports?actorId=a&resourceType=ACCOUNT&resourceId=r").statusCode())
                .isEqualTo(400);
    }

    @Test
    void detectsSignedSemanticTamperingAcrossVerificationLayers() {
        append("RECORD_UPDATED", "actor-A", "resource-1", "visible");
        append("RECORD_UPDATED", "actor-B", "resource-2", "private");
        var bundle = createService.create(new ExportScope("actor-A", null, null));

        assertThat(verificationService.verify(new AuditExportBundle(null, null)).violationType())
                .hasToString("MALFORMED_BUNDLE");
        var missingSignatureValue = new AuditExportBundle(
                bundle.manifest(),
                new AuditExportSignature(
                        bundle.signature().algorithm(), bundle.signature().keyId(),
                        bundle.signature().publicKey(), null));
        assertViolation(missingSignatureValue, "SIGNATURE_INVALID");

        var payloadTampered = replaceRecord(bundle, 0, record -> copy(
                record, record.kind(), record.sequenceNumber(), record.eventType(), record.actorId(),
                Map.of("field", "tampered"), record.contentHash(), record.previousHash(), record.recordHash()));
        assertViolation(resign(payloadTampered), "PAYLOAD_PROOF_MISMATCH");

        var contentTampered = replaceRecord(bundle, 0, record -> copy(
                record, record.kind(), record.sequenceNumber(), "OTHER_EVENT", record.actorId(),
                record.payload(), record.contentHash(), record.previousHash(), record.recordHash()));
        assertViolation(resign(contentTampered), "CONTENT_HASH_MISMATCH");

        var bridgeTampered = replaceRecord(bundle, 1, record -> copy(
                record, record.kind(), record.sequenceNumber(), record.eventType(), record.actorId(),
                record.payload(), record.contentHash(), record.previousHash(), "f".repeat(64)));
        assertViolation(resign(bridgeTampered), "RECORD_HASH_MISMATCH");

        var wrongScope = withManifest(bundle, new AuditExportManifest(
                1, bundle.manifest().bundleId(), bundle.manifest().generatedAt(),
                new ExportScopeDescriptor("ACTOR", "different-actor", null, null),
                bundle.manifest().chainHead(), bundle.manifest().matchedEventCount(), bundle.manifest().records()));
        assertViolation(resign(wrongScope), "SCOPE_MISMATCH");

        var wrongCount = withManifest(bundle, new AuditExportManifest(
                1, bundle.manifest().bundleId(), bundle.manifest().generatedAt(), bundle.manifest().scope(),
                bundle.manifest().chainHead(), 7, bundle.manifest().records()));
        assertViolation(resign(wrongCount), "MATCH_COUNT_MISMATCH");

        var wrongHead = withManifest(bundle, new AuditExportManifest(
                1, bundle.manifest().bundleId(), bundle.manifest().generatedAt(), bundle.manifest().scope(),
                new ExportChainHead(2, "e".repeat(64)), bundle.manifest().matchedEventCount(),
                bundle.manifest().records()));
        assertViolation(resign(wrongHead), "CHAIN_HEAD_MISMATCH");

        var wrongSequence = replaceRecord(bundle, 0, record -> copy(
                record, record.kind(), 4, record.eventType(), record.actorId(), record.payload(),
                record.contentHash(), record.previousHash(), record.recordHash()));
        assertViolation(resign(wrongSequence), "SEQUENCE_GAP");

        var wrongPrevious = replaceRecord(bundle, 0, record -> copy(
                record, record.kind(), record.sequenceNumber(), record.eventType(), record.actorId(),
                record.payload(), record.contentHash(), "d".repeat(64), record.recordHash()));
        assertViolation(resign(wrongPrevious), "PREVIOUS_HASH_MISMATCH");

        var wrongKind = replaceRecord(bundle, 1, record -> copy(
                record, "UNKNOWN", record.sequenceNumber(), record.eventType(), record.actorId(),
                record.payload(), record.contentHash(), record.previousHash(), record.recordHash()));
        assertViolation(resign(wrongKind), "UNSUPPORTED_RECORD_KIND");
    }

    private void append(String eventType, String actorId, String resourceId, String field) {
        appendService.append(new AppendAuditEventCommand(
                eventType,
                actorId,
                "ACCOUNT",
                resourceId,
                Map.of("field", field),
                Instant.parse("2026-08-15T10:15:30.123456Z")));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private List<String> kinds(JsonNode bundle) {
        var result = new ArrayList<String>();
        bundle.get("manifest").get("records").forEach(record -> result.add(record.get("kind").asString()));
        return result;
    }

    private AuditExportBundle replaceRecord(
            AuditExportBundle bundle,
            int index,
            java.util.function.UnaryOperator<AuditExportRecord> replacement) {
        var records = new ArrayList<>(bundle.manifest().records());
        records.set(index, replacement.apply(records.get(index)));
        return withManifest(bundle, new AuditExportManifest(
                bundle.manifest().bundleVersion(), bundle.manifest().bundleId(), bundle.manifest().generatedAt(),
                bundle.manifest().scope(), bundle.manifest().chainHead(),
                bundle.manifest().matchedEventCount(), records));
    }

    private AuditExportRecord copy(
            AuditExportRecord source,
            String kind,
            long sequence,
            String eventType,
            String actorId,
            Map<String, Object> payload,
            String contentHash,
            String previousHash,
            String recordHash) {
        return new AuditExportRecord(
                kind, sequence, source.eventId(), eventType, actorId, source.resourceType(), source.resourceId(),
                payload, source.payloadProofs(), source.timestamp(), source.recordedAt(), source.hashVersion(),
                contentHash, previousHash, recordHash);
    }

    private AuditExportBundle withManifest(AuditExportBundle bundle, AuditExportManifest manifest) {
        return new AuditExportBundle(manifest, bundle.signature());
    }

    private AuditExportBundle resign(AuditExportBundle bundle) {
        return new AuditExportBundle(bundle.manifest(), signatureService.sign(bundle.manifest()));
    }

    private void assertViolation(AuditExportBundle bundle, String expected) {
        assertThat(verificationService.verify(bundle).violationType()).hasToString(expected);
    }
}
