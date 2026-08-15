package com.jaswanth.auditlog.shared.security;

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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url="
                + "jdbc:h2:mem:audit-security-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("h2")
class SecurityIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void enforcesAuthenticationRolesAndAssignmentVerificationAlias() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            assertThat(get(client, "/actuator/health", null).statusCode()).isEqualTo(200);

            var unauthenticated = get(client, "/audit/events", null);
            assertThat(unauthenticated.statusCode()).isEqualTo(401);
            assertThat(unauthenticated.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE))
                    .contains("Basic realm=\"audit-log-service\"");
            var unauthorizedProblem = objectMapper.readTree(unauthenticated.body());
            assertThat(unauthorizedProblem.get("title").asString()).isEqualTo("Authentication required");
            assertThat(unauthorizedProblem.get("status").asInt()).isEqualTo(401);
            assertThat(get(client, "/audit/events", "Basic invalid-credentials").statusCode())
                    .isEqualTo(401);
            assertThat(get(client, "/api-docs", null).statusCode()).isEqualTo(401);
            assertThat(get(client, "/api-docs", TestCredentials.READER).statusCode()).isEqualTo(200);

            var created = post(client, "/audit/events", TestCredentials.WRITER, """
                    {
                      "eventType": "USER_LOGIN",
                      "actorId": "actor-1",
                      "resourceType": "ACCOUNT",
                      "resourceId": "account-1",
                      "payload": {"source": "security-test"}
                    }
                    """);
            assertThat(created.statusCode()).isEqualTo(201);

            var forbidden = get(client, "/audit/events", TestCredentials.WRITER);
            assertThat(forbidden.statusCode()).isEqualTo(403);
            var forbiddenProblem = objectMapper.readTree(forbidden.body());
            assertThat(forbiddenProblem.get("title").asString()).isEqualTo("Access denied");
            assertThat(forbiddenProblem.get("status").asInt()).isEqualTo(403);

            assertThat(get(client, "/audit/events", TestCredentials.READER).statusCode()).isEqualTo(200);
            assertThat(post(client, "/audit/events", TestCredentials.READER, "{}").statusCode()).isEqualTo(403);

            var assignmentAlias = get(client, "/audit/verify", TestCredentials.READER);
            assertThat(assignmentAlias.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(assignmentAlias.body()).get("valid").asBoolean()).isTrue();
            assertThat(get(client, "/audit/verification", TestCredentials.READER).statusCode()).isEqualTo(200);

            assertThat(get(client, "/audit/exports?actorId=actor-1", TestCredentials.READER).statusCode())
                    .isEqualTo(403);
            assertThat(get(client, "/actuator/prometheus", TestCredentials.READER).statusCode())
                    .isEqualTo(403);
            assertThat(get(client, "/actuator/prometheus", TestCredentials.ADMIN).statusCode())
                    .isEqualTo(200);
            assertThat(get(client, "/not-an-api", TestCredentials.ADMIN).statusCode())
                    .isEqualTo(403);
        }
    }

    private HttpResponse<String> get(HttpClient client, String path, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(
            HttpClient client,
            String path,
            String authorization,
            String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
