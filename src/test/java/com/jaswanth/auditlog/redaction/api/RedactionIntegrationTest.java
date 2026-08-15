package com.jaswanth.auditlog.redaction.api;

import com.jaswanth.auditlog.audit.application.AppendAuditEventCommand;
import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
import com.jaswanth.auditlog.audit.domain.AuditHashService;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import com.jaswanth.auditlog.support.TestCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url="
                + "jdbc:h2:mem:audit-redaction-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("h2")
class RedactionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AppendAuditEventService appendService;

    @Autowired
    private AuditEventRepository eventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void irreversiblyRedactsACommittedLeafWithoutBreakingVerification() throws Exception {
        var event = appendService.append(new AppendAuditEventCommand(
                "CLIENT_DATA_ACCESSED",
                "advisor-7",
                "CLIENT_ACCOUNT",
                "account-9",
                Map.of(
                        "customer", Map.of("name", "Jaswanth", "ssn", "111-22-3333"),
                        "purpose", "support"),
                Instant.parse("2026-08-15T10:15:30.123456Z")));
        assertThat(event.hashVersion()).isEqualTo(AuditHashService.CURRENT_HASH_VERSION);

        try (var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            assertThat(get(httpClient, "/audit/verification").get("valid").asBoolean()).isTrue();

            var redaction = post(httpClient, event.eventId(), """
                    {
                      "actorId":"privacy-officer-1",
                      "reason":"retention request",
                      "paths":["/customer/ssn"]
                    }
                    """);
            assertThat(redaction.statusCode()).isEqualTo(200);
            var redactionBody = objectMapper.readTree(redaction.body());
            assertThat(redactionBody.get("redactedPaths").get(0).asString())
                    .isEqualTo("/customer/ssn");
            assertThat(redactionBody.get("receiptEventId").asString()).isNotBlank();

            var page = get(httpClient, "/audit/events");
            var payload = page.get("items").get(0).get("payload");
            assertThat(payload.get("customer").get("name").asString()).isEqualTo("Jaswanth");
            assertThat(payload.get("customer").get("ssn").has("$redacted")).isTrue();
            assertThat(page.toString()).doesNotContain("111-22-3333");
            assertThat(page.get("items").get(1).get("eventType").asString())
                    .isEqualTo("AUDIT_PAYLOAD_REDACTED");

            var verification = get(httpClient, "/audit/verification");
            assertThat(verification.get("valid").asBoolean()).isTrue();
            assertThat(verification.get("verifiedEventCount").asLong()).isEqualTo(2);

            assertThat(post(httpClient, event.eventId(), """
                    {
                      "actorId":"privacy-officer-1",
                      "reason":"duplicate request",
                      "paths":["/customer/ssn"]
                    }
                    """).statusCode()).isEqualTo(409);
            assertThat(post(httpClient, event.eventId(), """
                    {
                      "actorId":"privacy-officer-1",
                      "reason":"invalid path test",
                      "paths":["/customer/missing"]
                    }
                    """).statusCode()).isEqualTo(409);
            assertThat(post(httpClient, UUID.randomUUID(), """
                    {
                      "actorId":"privacy-officer-1",
                      "reason":"missing event test",
                      "paths":["/customer/ssn"]
                    }
                    """).statusCode()).isEqualTo(404);
            assertThat(post(httpClient, event.eventId(), """
                    {
                      "actorId":"privacy-officer-1",
                      "reason":"validation test",
                      "paths":["not-a-pointer"]
                    }
                    """).statusCode()).isEqualTo(400);

            jdbcTemplate.update(
                    "UPDATE audit_event SET hash_version = ? WHERE sequence_number = ?",
                    AuditHashService.HASH_VERSION,
                    event.sequenceNumber());
            assertThat(post(httpClient, event.eventId(), """
                    {
                      "actorId":"privacy-officer-1",
                      "reason":"legacy compatibility test",
                      "paths":["/customer/name"]
                    }
                    """).statusCode()).isEqualTo(409);
        }

        var stored = eventRepository.findById(event.sequenceNumber()).orElseThrow();
        assertThat(stored.isRedacted()).isTrue();
        assertThat(stored.getRedactedAt()).isNotNull();
        assertThat(proofAt(stored.getPayloadProofs(), "/customer/ssn").get("salt")).isNull();
    }

    private HttpResponse<String> post(HttpClient client, UUID eventId, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/audit/events/" + eventId + "/redactions"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", TestCredentials.ADMIN)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", TestCredentials.ADMIN)
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> proofAt(Map<String, Object> proofs, String path) {
        return (Map<String, Object>) proofs.get(path);
    }
}
