package com.jaswanth.auditlog.audit.api;

import com.jaswanth.auditlog.audit.application.AppendAuditEventCommand;
import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
import com.jaswanth.auditlog.audit.domain.AuditEventContent;
import com.jaswanth.auditlog.audit.domain.AuditHashService;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditChainHeadRepository;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventEntity;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import com.jaswanth.auditlog.support.TestCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
class AuditEventAppendIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppendAuditEventService appendService;

    @Autowired
    private AuditHashService hashService;

    @Autowired
    private AuditEventRepository eventRepository;

    @Autowired
    private AuditChainHeadRepository chainHeadRepository;

    @Test
    void appendsValidatedEventsAndSerializesConcurrentWriters() throws Exception {
        try (var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var invalidResponse = post(httpClient, """
                    {
                      "eventType": "USER_LOGIN",
                      "actorId": " ",
                      "resourceType": "ACCOUNT",
                      "resourceId": "account-1",
                      "payload": {}
                    }
                    """);
            assertThat(invalidResponse.statusCode()).isEqualTo(400);

            var firstResponse = post(httpClient, """
                    {
                      "eventType": "RECORD_UPDATED",
                      "actorId": "actor-1",
                      "resourceType": "ACCOUNT",
                      "resourceId": "account-1",
                      "payload": {"z": 2, "nested": {"z": "last", "a": "first"}, "a": 1},
                      "timestamp": "2026-08-15T10:15:30.123456789Z"
                    }
                    """);
            assertThat(firstResponse.statusCode()).isEqualTo(201);
            assertThat(firstResponse.headers().firstValue("Location"))
                    .hasValueSatisfying(location -> assertThat(location).contains("/audit/events/"));

            JsonNode first = objectMapper.readTree(firstResponse.body());
            assertThat(first.get("sequenceNumber").asLong()).isEqualTo(1);
            assertThat(first.get("timestamp").asString()).isEqualTo("2026-08-15T10:15:30.123456Z");
            assertThat(first.get("previousHash").asString()).isEqualTo(AuditHashService.GENESIS_HASH);
            assertThat(first.get("contentHash").asString()).matches("[0-9a-f]{64}");
            assertThat(first.get("recordHash").asString()).matches("[0-9a-f]{64}");

            var secondResponse = post(httpClient, """
                    {
                      "eventType": "PERMISSION_GRANTED",
                      "actorId": "actor-2",
                      "resourceType": "ACCOUNT",
                      "resourceId": "account-1",
                      "payload": {"role": "VIEWER"}
                    }
                    """);
            assertThat(secondResponse.statusCode()).isEqualTo(201);
            JsonNode second = objectMapper.readTree(secondResponse.body());
            assertThat(second.get("previousHash").asString()).isEqualTo(first.get("recordHash").asString());
            assertThat(second.get("timestamp").asString()).isEqualTo(second.get("recordedAt").asString());
        }

        appendConcurrently(8);

        var events = eventRepository.findAll(Sort.by("sequenceNumber"));
        assertThat(events).hasSize(10);
        assertChainLinks(events);

        var chainHead = chainHeadRepository.findById((short) 1).orElseThrow();
        var lastEvent = events.getLast();
        assertThat(chainHead.getLastSequence()).isEqualTo(lastEvent.getSequenceNumber());
        assertThat(chainHead.getLastHash()).isEqualTo(lastEvent.getRecordHash());
    }

    private HttpResponse<String> post(HttpClient httpClient, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/audit/events"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", TestCredentials.ADMIN)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void appendConcurrently(int eventCount) throws Exception {
        try (var executor = Executors.newFixedThreadPool(eventCount)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int index = 0; index < eventCount; index++) {
                var eventIndex = index;
                futures.add(executor.submit(() -> appendService.append(new AppendAuditEventCommand(
                        "CONCURRENT_EVENT",
                        "actor-" + eventIndex,
                        "ACCOUNT",
                        "account-concurrent",
                        Map.of("index", eventIndex),
                        null))));
            }
            for (var future : futures) {
                future.get();
            }
        }
    }

    private void assertChainLinks(java.util.List<AuditEventEntity> events) {
        var expectedPreviousHash = AuditHashService.GENESIS_HASH;
        for (var event : events) {
            assertThat(event.getPreviousHash()).isEqualTo(expectedPreviousHash);
            var recomputed = hashService.calculateForVerification(new AuditEventContent(
                    event.getEventType(),
                    event.getActorId(),
                    event.getResourceType(),
                    event.getResourceId(),
                    event.getPayload(),
                    event.getOccurredAt()),
                    expectedPreviousHash,
                    event.getHashVersion(),
                    event.getPayloadProofs());
            assertThat(event.getContentHash()).isEqualTo(recomputed.contentHash());
            assertThat(event.getRecordHash()).isEqualTo(recomputed.recordHash());
            expectedPreviousHash = event.getRecordHash();
        }
    }
}
