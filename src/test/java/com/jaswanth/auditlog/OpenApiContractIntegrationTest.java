package com.jaswanth.auditlog;

import com.jaswanth.auditlog.support.TestCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url="
                + "jdbc:h2:mem:openapi-contract-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("h2")
class OpenApiContractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publishesRequiredOperationsWithoutAuditEventUpdateOrDelete() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api-docs"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", TestCredentials.READER)
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            var document = objectMapper.readTree(response.body());
            var paths = document.get("paths");

            assertThat(paths.propertyNames()).contains(
                    "/audit/events",
                    "/audit/verify",
                    "/audit/events/{eventId}/redactions",
                    "/audit/retention/runs",
                    "/audit/exports",
                    "/audit/exports/verification",
                    "/compliance/access-events",
                    "/compliance/access-reports",
                    "/compliance/access-exports",
                    "/compliance/access-exports/verification");

            var auditEventOperations = paths.get("/audit/events");
            assertThat(auditEventOperations.propertyNames())
                    .containsExactlyInAnyOrderElementsOf(Set.of("get", "post"));
            assertThat(document.at("/components/securitySchemes/basicAuth").isMissingNode()).isFalse();
            assertThat(document.at("/components/securitySchemes/bearerAuth").isMissingNode()).isFalse();
        }
    }
}
