package com.jaswanth.auditlog.compliance.api;

import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import com.jaswanth.auditlog.compliance.application.ComplianceAccessCommand;
import com.jaswanth.auditlog.compliance.application.ComplianceIdentity;
import com.jaswanth.auditlog.compliance.application.RecordComplianceAccessService;
import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.AccessReasonCode;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;
import com.jaswanth.auditlog.compliance.export.application.ComplianceExportCriteriaHasher;
import com.jaswanth.auditlog.compliance.export.application.CreateComplianceExportService;
import com.jaswanth.auditlog.compliance.export.application.VerifyComplianceExportService;
import com.jaswanth.auditlog.compliance.export.model.ComplianceExportBundle;
import com.jaswanth.auditlog.compliance.export.model.ComplianceExportCriteria;
import com.jaswanth.auditlog.compliance.export.model.ComplianceExportManifest;
import com.jaswanth.auditlog.export.infrastructure.ExportSignatureService;
import com.jaswanth.auditlog.support.TestCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url="
                + "jdbc:h2:mem:compliance-export-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("h2")
class ComplianceExportIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RecordComplianceAccessService recordService;

    @Autowired
    private AuditEventRepository eventRepository;

    @Autowired
    private VerifyComplianceExportService verificationService;

    @Autowired
    private ComplianceExportCriteriaHasher criteriaHasher;

    @Autowired
    private ExportSignatureService signatureService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void recordFixtures() {
        if (eventRepository.count() > 0) {
            return;
        }
        record("actor-1", "source-a", "account-a", AccessAction.VIEW, AccessOutcome.ALLOWED,
                ClientDataCategory.BALANCES, Instant.parse("2026-08-15T10:10:00Z"));
        record("actor-2", "source-b", "account-a", AccessAction.DOWNLOAD, AccessOutcome.DENIED,
                ClientDataCategory.TRANSACTIONS, Instant.parse("2026-08-15T10:20:00Z"));
        record("actor-1", "source-a", "account-b", AccessAction.VIEW, AccessOutcome.ALLOWED,
                ClientDataCategory.POSITIONS, Instant.parse("2026-08-15T10:30:00Z"));
    }

    @Test
    void createsPrivateSignedCriteriaBoundEvidenceAndDetectsTampering() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var path = "/compliance/access-exports?accountId=account-a"
                    + "&from=2026-08-15T10:00:00Z&to=2026-08-15T11:00:00Z"
                    + "&reportPurpose=REGULATORY&action=VIEW";
            assertThat(get(client, path, null).statusCode()).isEqualTo(401);
            assertThat(get(client, path, TestCredentials.READER).statusCode()).isEqualTo(403);

            var response = get(client, path, TestCredentials.ADMIN);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION).orElseThrow())
                    .startsWith("attachment; filename=\"compliance-access-export-");
            assertThat(response.body()).doesNotContain("actor-2", "account-b", "TRANSACTIONS", "POSITIONS");

            var json = objectMapper.readTree(response.body());
            var manifest = json.get("manifest");
            assertThat(manifest.get("bundleType").asString()).isEqualTo("COMPLIANCE_ACCESS_REPORT");
            assertThat(manifest.get("criteria").get("accountId").asString()).isEqualTo("account-a");
            assertThat(manifest.get("criteria").get("reportPurpose").asString()).isEqualTo("REGULATORY");
            assertThat(manifest.get("criteriaHash").asString()).matches("[0-9a-f]{64}");
            assertThat(manifest.get("chainHead").get("sequenceNumber").asLong()).isEqualTo(3);
            assertThat(manifest.get("matchedEventCount").asLong()).isEqualTo(1);
            assertThat(manifest.get("records")).hasSize(3);
            assertThat(manifest.get("records").get(0).get("kind").asString()).isEqualTo("FULL");
            assertThat(manifest.get("records").get(1).get("kind").asString()).isEqualTo("BRIDGE");
            assertThat(manifest.get("records").get(2).get("kind").asString()).isEqualTo("BRIDGE");

            var verification = post(
                    client,
                    "/compliance/access-exports/verification",
                    TestCredentials.READER,
                    response.body());
            assertThat(verification.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(verification.body()).get("valid").asBoolean()).isTrue();

            var tampered = response.body().replace("BALANCES", "POSITIONS");
            var tamperedVerification = post(
                    client,
                    "/compliance/access-exports/verification",
                    TestCredentials.READER,
                    tampered);
            assertThat(objectMapper.readTree(tamperedVerification.body()).get("violationType").asString())
                    .isEqualTo("SIGNATURE_INVALID");

            var receipts = eventRepository.findAll().stream()
                    .filter(event -> CreateComplianceExportService.RECEIPT_EVENT_TYPE.equals(event.getEventType()))
                    .toList();
            assertThat(receipts).hasSize(1);
            var receipt = receipts.getFirst();
            assertThat(receipt.getActorId()).isEqualTo("audit-admin");
            assertThat(receipt.getPayload())
                    .containsEntry("reportPurpose", "REGULATORY");
            assertThat(((Number) receipt.getPayload().get("matchedEventCount")).longValue()).isEqualTo(1);
            assertThat(receipt.getPayload().toString())
                    .doesNotContain("account-a", "account-b", "actor-1", "actor-2");

            var bundle = objectMapper.readValue(response.body(), ComplianceExportBundle.class);
            var wrongHashManifest = copyManifest(bundle.manifest(), "f".repeat(64), bundle.manifest().criteria());
            var wrongHash = new ComplianceExportBundle(
                    wrongHashManifest, signatureService.sign(wrongHashManifest));
            assertThat(verificationService.verify(wrongHash).violationType())
                    .hasToString("CRITERIA_HASH_MISMATCH");

            var changedCriteria = new ComplianceExportCriteria(
                    "account-b",
                    null,
                    bundle.manifest().criteria().from(),
                    bundle.manifest().criteria().to(),
                    bundle.manifest().criteria().reportPurpose(),
                    bundle.manifest().criteria().action(),
                    bundle.manifest().criteria().outcome(),
                    bundle.manifest().criteria().sourceSystem(),
                    bundle.manifest().criteria().dataCategory());
            var wrongScopeManifest = copyManifest(
                    bundle.manifest(), criteriaHasher.hash(changedCriteria), changedCriteria);
            var wrongScope = new ComplianceExportBundle(
                    wrongScopeManifest, signatureService.sign(wrongScopeManifest));
            assertThat(verificationService.verify(wrongScope).violationType())
                    .hasToString("SCOPE_MISMATCH");
        }
    }

    private ComplianceExportManifest copyManifest(
            ComplianceExportManifest source,
            String criteriaHash,
            ComplianceExportCriteria criteria) {
        return new ComplianceExportManifest(
                source.bundleVersion(),
                source.bundleType(),
                source.bundleId(),
                source.generatedAt(),
                criteriaHash,
                criteria,
                source.chainHead(),
                source.matchedEventCount(),
                source.records());
    }

    private void record(
            String actor,
            String source,
            String account,
            AccessAction action,
            AccessOutcome outcome,
            ClientDataCategory category,
            Instant timestamp) {
        recordService.record(new ComplianceAccessCommand(
                new ComplianceIdentity(actor, source),
                account,
                action,
                outcome,
                List.of(category),
                AccessPurpose.CUSTOMER_SERVICE,
                UUID.randomUUID(),
                outcome == AccessOutcome.ALLOWED
                        ? AccessReasonCode.POLICY_ALLOWED
                        : AccessReasonCode.ROLE_NOT_PERMITTED,
                timestamp));
    }

    private HttpResponse<String> get(HttpClient client, String path, String authorization) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(
            HttpClient client,
            String path,
            String authorization,
            String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .timeout(Duration.ofSeconds(10))
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
