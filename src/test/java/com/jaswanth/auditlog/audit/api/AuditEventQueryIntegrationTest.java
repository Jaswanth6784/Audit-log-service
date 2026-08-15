package com.jaswanth.auditlog.audit.api;

import com.jaswanth.auditlog.audit.application.AppendAuditEventCommand;
import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
import com.jaswanth.auditlog.support.TestCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
                + "jdbc:h2:mem:audit-query-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("h2")
class AuditEventQueryIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppendAuditEventService appendService;

    @Test
    void filtersAndPaginatesWithAStableIncrementalCursor() throws Exception {
        append("USER_LOGIN", "actor-A", "ACCOUNT", "resource-1", "2026-08-15T10:00:00Z");
        append("RECORD_UPDATED", "actor-A", "ACCOUNT", "resource-1", "2026-08-15T11:00:00Z");
        append("USER_LOGIN", "actor-B", "DOCUMENT", "resource-2", "2026-08-15T12:00:00Z");
        append("PERMISSION_GRANTED", "actor-A", "ACCOUNT", "resource-3", "2026-08-15T13:00:00Z");
        append("RECORD_UPDATED", "actor-B", "ACCOUNT", "resource-1", "2026-08-15T14:00:00Z");

        try (var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            assertSequences(getPage(httpClient, "?actorId=actor-A"), 1, 2, 4);
            assertSequences(getPage(httpClient, "?resourceType=ACCOUNT"), 1, 2, 4, 5);
            assertSequences(getPage(httpClient, "?resourceId=resource-1"), 1, 2, 5);
            assertSequences(getPage(httpClient, "?eventType=USER_LOGIN"), 1, 3);
            assertSequences(getPage(httpClient,
                    "?from=2026-08-15T11:00:00Z&to=2026-08-15T14:00:00Z"), 2, 3, 4);
            assertSequences(getPage(httpClient,
                    "?actorId=actor-A&resourceType=ACCOUNT&resourceId=resource-1&eventType=RECORD_UPDATED"), 2);

            var firstPage = getPage(httpClient, "?limit=2");
            assertSequences(firstPage, 1, 2);
            assertThat(firstPage.get("count").asInt()).isEqualTo(2);
            assertThat(firstPage.get("limit").asInt()).isEqualTo(2);
            assertThat(firstPage.get("hasMore").asBoolean()).isTrue();
            assertThat(firstPage.get("nextAfterSequence").asLong()).isEqualTo(2);

            var secondPage = getPage(httpClient, "?afterSequence=2&limit=2");
            assertSequences(secondPage, 3, 4);
            assertThat(secondPage.get("hasMore").asBoolean()).isTrue();
            assertThat(secondPage.get("nextAfterSequence").asLong()).isEqualTo(4);

            var finalPage = getPage(httpClient, "?afterSequence=4&limit=2");
            assertSequences(finalPage, 5);
            assertThat(finalPage.get("hasMore").asBoolean()).isFalse();
            assertThat(finalPage.get("nextAfterSequence").asLong()).isEqualTo(5);

            var pollingPage = getPage(httpClient, "?afterSequence=5&limit=2");
            assertSequences(pollingPage);
            assertThat(pollingPage.get("hasMore").asBoolean()).isFalse();
            assertThat(pollingPage.get("nextAfterSequence").asLong()).isEqualTo(5);

            assertThat(get(httpClient,
                    "?from=2026-08-15T14:00:00Z&to=2026-08-15T11:00:00Z").statusCode()).isEqualTo(400);
            assertThat(get(httpClient, "?limit=201").statusCode()).isEqualTo(400);
            assertThat(get(httpClient, "?afterSequence=-1").statusCode()).isEqualTo(400);
            assertThat(get(httpClient, "?actorId=%20").statusCode()).isEqualTo(400);
        }
    }

    private void append(
            String eventType,
            String actorId,
            String resourceType,
            String resourceId,
            String timestamp) {
        appendService.append(new AppendAuditEventCommand(
                eventType,
                actorId,
                resourceType,
                resourceId,
                Map.of("fixture", resourceId),
                Instant.parse(timestamp)));
    }

    private JsonNode getPage(HttpClient httpClient, String query) throws Exception {
        var response = get(httpClient, query);
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> get(HttpClient httpClient, String query) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/audit/events" + query))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", TestCredentials.READER)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertSequences(JsonNode page, long... expectedSequences) {
        var items = page.get("items");
        assertThat(items).hasSize(expectedSequences.length);
        for (int index = 0; index < expectedSequences.length; index++) {
            assertThat(items.get(index).get("sequenceNumber").asLong()).isEqualTo(expectedSequences[index]);
        }
    }
}
