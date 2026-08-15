package com.jaswanth.auditlog.compliance.api;

import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import com.jaswanth.auditlog.compliance.application.RecordComplianceAccessService;
import com.jaswanth.auditlog.support.TestCredentials;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url="
                + "jdbc:h2:mem:compliance-access-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("h2")
class ComplianceAccessEventIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditEventRepository eventRepository;

    @Test
    void recordsOnlyAuthenticatedNormalizedComplianceEvidence() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var body = """
                    {
                      "accountId": "account-123",
                      "action": "VIEW",
                      "outcome": "ALLOWED",
                      "dataCategories": ["TRANSACTIONS", "BALANCES", "TRANSACTIONS"],
                      "purposeCode": "CUSTOMER_SERVICE",
                      "correlationId": "d1d109d1-7e82-45e8-95b8-0503416c0f38",
                      "reasonCode": "POLICY_ALLOWED",
                      "timestamp": "2026-08-15T10:15:30.123456789Z"
                    }
                    """;

            assertThat(post(client, null, body).statusCode()).isEqualTo(401);
            assertThat(post(client, TestCredentials.READER, body).statusCode()).isEqualTo(403);

            var response = post(client, TestCredentials.WRITER, body);
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.headers().firstValue(HttpHeaders.LOCATION).orElseThrow())
                    .startsWith("/compliance/access-events/");
            var recorded = objectMapper.readTree(response.body());
            assertThat(recorded.get("actorId").asString()).isEqualTo("audit-writer");
            assertThat(recorded.get("sourceSystem").asString()).isEqualTo("LOCAL_H2_DEMO");
            assertThat(recorded.get("accountId").asString()).isEqualTo("account-123");
            var categories = recorded.get("dataCategories");
            assertThat(categories).hasSize(2);
            assertThat(categories.get(0).asString()).isEqualTo("BALANCES");
            assertThat(categories.get(1).asString()).isEqualTo("TRANSACTIONS");
            assertThat(recorded.get("timestamp").asString())
                    .isEqualTo("2026-08-15T10:15:30.123456Z");
            assertThat(recorded.get("recordHash").asString()).matches("[0-9a-f]{64}");

            var stored = eventRepository.findAll().getFirst();
            assertThat(stored.getEventType()).isEqualTo(RecordComplianceAccessService.EVENT_TYPE);
            assertThat(stored.getResourceType()).isEqualTo(RecordComplianceAccessService.RESOURCE_TYPE);
            assertThat(stored.getResourceId()).isEqualTo("account-123");
            assertThat(stored.getActorId()).isEqualTo("audit-writer");
            assertThat(stored.getPayload())
                    .containsEntry("action", "VIEW")
                    .containsEntry("outcome", "ALLOWED")
                    .containsEntry("purposeCode", "CUSTOMER_SERVICE")
                    .containsEntry("sourceSystem", "LOCAL_H2_DEMO")
                    .containsEntry("correlationId", "d1d109d1-7e82-45e8-95b8-0503416c0f38")
                    .containsEntry("reasonCode", "POLICY_ALLOWED")
                    .containsEntry("dataCategories", List.of("BALANCES", "TRANSACTIONS"))
                    .doesNotContainKeys("actorId", "payload", "accountNumber");

            var spoofed = body.replace(
                    "\"accountId\": \"account-123\"",
                    "\"accountId\": \"account-123\", \"actorId\": \"spoofed-actor\"");
            assertThat(post(client, TestCredentials.WRITER, spoofed).statusCode()).isEqualTo(400);
            var arbitraryPayload = body.replace(
                    "\"accountId\": \"account-123\"",
                    "\"accountId\": \"account-123\", \"payload\": {\"ssn\": \"sensitive\"}");
            assertThat(post(client, TestCredentials.WRITER, arbitraryPayload).statusCode()).isEqualTo(400);
            assertThat(post(client, TestCredentials.WRITER, body.replace("\"VIEW\"", "\"DELETE\""))
                    .statusCode()).isEqualTo(400);
            assertThat(eventRepository.count()).isEqualTo(1);

            var verification = get(client, "/audit/verify", TestCredentials.READER);
            assertThat(verification.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(verification.body()).get("valid").asBoolean()).isTrue();
        }
    }

    private HttpResponse<String> post(HttpClient client, String authorization, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/compliance/access-events"))
                .timeout(Duration.ofSeconds(10))
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return client.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient client, String path, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
