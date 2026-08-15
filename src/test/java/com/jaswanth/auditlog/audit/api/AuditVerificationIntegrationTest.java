package com.jaswanth.auditlog.audit.api;

import com.jaswanth.auditlog.audit.application.AppendAuditEventCommand;
import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
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
        properties = "spring.datasource.url="
                + "jdbc:h2:mem:audit-verification-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("h2")
class AuditVerificationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AppendAuditEventService appendService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void verifiesACleanChainAndDetectsDirectDatabaseTampering() throws Exception {
        append("USER_LOGIN", "actor-A");
        append("RECORD_UPDATED", "actor-B");
        append("PERMISSION_GRANTED", "actor-C");

        try (var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var clean = verify(httpClient);
            assertThat(clean.get("valid").asBoolean()).isTrue();
            assertThat(clean.get("verifiedEventCount").asLong()).isEqualTo(3);
            assertThat(clean.get("chainHeadSequence").asLong()).isEqualTo(3);
            assertThat(clean.get("firstInvalidSequence").isNull()).isTrue();
            assertThat(clean.get("violationType").isNull()).isTrue();

            var changedRows = jdbcTemplate.update(
                    "UPDATE audit_event SET actor_id = ? WHERE sequence_number = ?",
                    "database-attacker",
                    2L);
            assertThat(changedRows).isEqualTo(1);

            var tampered = verify(httpClient);
            assertThat(tampered.get("valid").asBoolean()).isFalse();
            assertThat(tampered.get("verifiedEventCount").asLong()).isEqualTo(1);
            assertThat(tampered.get("firstInvalidSequence").asLong()).isEqualTo(2);
            assertThat(tampered.get("violationType").asString()).isEqualTo("CONTENT_HASH_MISMATCH");
        }
    }

    private void append(String eventType, String actorId) {
        appendService.append(new AppendAuditEventCommand(
                eventType,
                actorId,
                "ACCOUNT",
                "resource-1",
                Map.of("field", eventType),
                Instant.parse("2026-08-15T10:15:30.123456Z")));
    }

    private JsonNode verify(HttpClient httpClient) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/audit/verification"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }
}
