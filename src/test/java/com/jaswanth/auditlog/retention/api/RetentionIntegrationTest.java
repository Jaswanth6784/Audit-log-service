package com.jaswanth.auditlog.retention.api;

import com.jaswanth.auditlog.audit.application.AppendAuditEventCommand;
import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url="
                        + "jdbc:h2:mem:audit-retention-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "audit.retention.enabled=false",
                "audit.retention.max-age=30d",
                "audit.retention.batch-size=2"
        })
@ActiveProfiles("h2")
class RetentionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AppendAuditEventService appendService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void archivesBoundedBatchesWithoutBreakingFullChainVerification() throws Exception {
        append("EVENT_1");
        append("EVENT_2");
        append("EVENT_3");
        append("EVENT_4");
        jdbcTemplate.update(
                "UPDATE audit_event SET recorded_at = ? WHERE sequence_number <= ?",
                Instant.parse("2020-01-01T00:00:00Z"),
                3L);
        jdbcTemplate.update(
                "UPDATE audit_event SET recorded_at = ? WHERE sequence_number = ?",
                Instant.parse("9999-01-01T00:00:00Z"),
                4L);

        try (var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var firstRun = postRetention(httpClient);
            assertThat(firstRun.get("archivedCount").asInt()).isEqualTo(2);
            assertThat(firstRun.get("hasMoreEligibleEvents").asBoolean()).isTrue();
            assertSequences(get(httpClient, "/audit/events"), 3, 4);

            var secondRun = postRetention(httpClient);
            assertThat(secondRun.get("archivedCount").asInt()).isEqualTo(1);
            assertThat(secondRun.get("hasMoreEligibleEvents").asBoolean()).isFalse();
            assertSequences(get(httpClient, "/audit/events"), 4);

            var emptyRun = postRetention(httpClient);
            assertThat(emptyRun.get("archivedCount").asInt()).isZero();
            assertThat(emptyRun.get("hasMoreEligibleEvents").asBoolean()).isFalse();

            var verification = get(httpClient, "/audit/verification");
            assertThat(verification.get("valid").asBoolean()).isTrue();
            assertThat(verification.get("verifiedEventCount").asLong()).isEqualTo(4);
        }

        var archivedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE archived_at IS NOT NULL",
                Long.class);
        assertThat(archivedCount).isEqualTo(3);
    }

    private void append(String eventType) {
        appendService.append(new AppendAuditEventCommand(
                eventType,
                "actor-retention",
                "ACCOUNT",
                "resource-retention",
                Map.of("event", eventType),
                Instant.parse("2026-08-15T10:15:30.123456Z")));
    }

    private JsonNode postRetention(HttpClient httpClient) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/audit/retention/runs"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", TestCredentials.ADMIN)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return send(httpClient, request);
    }

    private JsonNode get(HttpClient httpClient, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", TestCredentials.ADMIN)
                .GET()
                .build();
        return send(httpClient, request);
    }

    private JsonNode send(HttpClient httpClient, HttpRequest request) throws Exception {
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private void assertSequences(JsonNode page, long... expectedSequences) {
        var items = page.get("items");
        assertThat(items).hasSize(expectedSequences.length);
        for (int index = 0; index < expectedSequences.length; index++) {
            assertThat(items.get(index).get("sequenceNumber").asLong()).isEqualTo(expectedSequences[index]);
        }
    }
}
