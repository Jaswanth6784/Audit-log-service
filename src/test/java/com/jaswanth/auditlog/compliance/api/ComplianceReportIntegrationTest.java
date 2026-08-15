package com.jaswanth.auditlog.compliance.api;

import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import com.jaswanth.auditlog.compliance.application.ComplianceAccessCommand;
import com.jaswanth.auditlog.compliance.application.ComplianceIdentity;
import com.jaswanth.auditlog.compliance.application.QueryComplianceReportService;
import com.jaswanth.auditlog.compliance.application.RecordComplianceAccessService;
import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.AccessReasonCode;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;
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
                + "jdbc:h2:mem:compliance-report-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("h2")
class ComplianceReportIntegrationTest {

    private static final Instant FIRST = Instant.parse("2026-08-15T10:10:00Z");
    private static final Instant SECOND = Instant.parse("2026-08-15T10:20:00Z");
    private static final Instant THIRD = Instant.parse("2026-08-15T10:30:00Z");

    @LocalServerPort
    private int port;

    @Autowired
    private RecordComplianceAccessService recordService;

    @Autowired
    private AuditEventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void recordFixtures() {
        if (eventRepository.count() > 0) {
            return;
        }
        record("actor-1", "source-a", "account-a", AccessAction.VIEW, AccessOutcome.ALLOWED,
                ClientDataCategory.BALANCES, FIRST);
        record("actor-2", "source-b", "account-a", AccessAction.DOWNLOAD, AccessOutcome.DENIED,
                ClientDataCategory.TRANSACTIONS, SECOND);
        record("actor-1", "source-a", "account-b", AccessAction.VIEW, AccessOutcome.ALLOWED,
                ClientDataCategory.POSITIONS, THIRD);
    }

    @Test
    void returnsStableMinimizedPagesAndAppendsPrivateAccessReceipts() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var accountQuery = "/compliance/access-reports?accountId=account-a"
                    + "&from=2026-08-15T10:00:00Z&to=2026-08-15T11:00:00Z"
                    + "&reportPurpose=REGULATORY&limit=1";
            assertThat(get(client, accountQuery, null).statusCode()).isEqualTo(401);
            assertThat(get(client, accountQuery, TestCredentials.WRITER).statusCode()).isEqualTo(403);

            var missingScope = "/compliance/access-reports?from=2026-08-15T10:00:00Z"
                    + "&to=2026-08-15T11:00:00Z&reportPurpose=REGULATORY";
            assertThat(get(client, missingScope, TestCredentials.READER).statusCode()).isEqualTo(400);
            var bothScopes = accountQuery + "&actorId=actor-1";
            assertThat(get(client, bothScopes, TestCredentials.READER).statusCode()).isEqualTo(400);
            var invalidRange = accountQuery.replace(
                    "from=2026-08-15T10:00:00Z", "from=2026-08-15T11:00:00Z");
            assertThat(get(client, invalidRange, TestCredentials.READER).statusCode()).isEqualTo(400);
            var missingPurpose = accountQuery.replace("&reportPurpose=REGULATORY", "");
            assertThat(get(client, missingPurpose, TestCredentials.READER).statusCode()).isEqualTo(400);
            assertThat(eventRepository.count()).isEqualTo(3);

            var firstResponse = get(client, accountQuery, TestCredentials.READER);
            assertThat(firstResponse.statusCode()).isEqualTo(200);
            var firstPage = objectMapper.readTree(firstResponse.body());
            assertThat(firstPage.get("items")).hasSize(1);
            assertThat(firstPage.get("hasMore").asBoolean()).isTrue();
            assertThat(firstPage.get("capturedThroughSequence").asLong()).isEqualTo(3);
            assertThat(firstPage.get("criteriaHash").asString()).matches("[0-9a-f]{64}");
            var firstItem = firstPage.get("items").get(0);
            assertThat(firstItem.get("accountId").asString()).isEqualTo("account-a");
            assertThat(firstItem.get("actorId").asString()).isEqualTo("actor-1");
            assertThat(firstItem.get("action").asString()).isEqualTo("VIEW");
            assertThat(firstItem.has("payload")).isFalse();
            assertThat(firstItem.has("contentHash")).isFalse();
            assertThat(firstItem.has("payloadProofs")).isFalse();

            var firstCursor = firstPage.get("nextAfterSequence").asLong();
            var secondResponse = get(
                    client,
                    accountQuery + "&afterSequence=" + firstCursor,
                    TestCredentials.READER);
            var secondPage = objectMapper.readTree(secondResponse.body());
            assertThat(secondPage.get("items")).hasSize(1);
            assertThat(secondPage.get("items").get(0).get("actorId").asString()).isEqualTo("actor-2");
            assertThat(secondPage.get("hasMore").asBoolean()).isFalse();
            assertThat(secondPage.get("nextAfterSequence").asLong()).isEqualTo(2);

            var filteredQuery = "/compliance/access-reports?actorId=actor-1"
                    + "&from=2026-08-15T10:00:00Z&to=2026-08-15T11:00:00Z"
                    + "&reportPurpose=REGULATORY"
                    + "&action=VIEW&outcome=ALLOWED&sourceSystem=source-a&dataCategory=POSITIONS";
            var filtered = objectMapper.readTree(
                    get(client, filteredQuery, TestCredentials.READER).body());
            assertThat(filtered.get("items")).hasSize(1);
            assertThat(filtered.get("items").get(0).get("accountId").asString()).isEqualTo("account-b");

            var halfOpenQuery = "/compliance/access-reports?actorId=actor-1"
                    + "&from=2026-08-15T10:00:00Z&to=2026-08-15T10:30:00Z"
                    + "&reportPurpose=SECURITY_INVESTIGATION";
            var halfOpen = objectMapper.readTree(
                    get(client, halfOpenQuery, TestCredentials.READER).body());
            assertThat(halfOpen.get("items")).hasSize(1);
            assertThat(halfOpen.get("items").get(0).get("accountId").asString()).isEqualTo("account-a");

            var receipts = eventRepository.findAll().stream()
                    .filter(event -> QueryComplianceReportService.RECEIPT_EVENT_TYPE.equals(event.getEventType()))
                    .toList();
            assertThat(receipts).hasSize(4);
            assertThat(receipts).allSatisfy(receipt -> {
                assertThat(receipt.getActorId()).isEqualTo("audit-reader");
                assertThat(receipt.getResourceType())
                        .isEqualTo(QueryComplianceReportService.RECEIPT_RESOURCE_TYPE);
                assertThat(receipt.getResourceId()).matches("[0-9a-f]{64}");
                assertThat(receipt.getPayload().toString())
                        .doesNotContain("account-a", "account-b", "actor-1", "actor-2");
                assertThat(receipt.getPayload()).containsKey("reportPurpose");
            });

            var verification = get(client, "/audit/verify", TestCredentials.READER);
            assertThat(verification.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(verification.body()).get("valid").asBoolean()).isTrue();
        }
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
}
