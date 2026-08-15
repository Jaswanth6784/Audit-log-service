package com.jaswanth.auditlog.compliance.api;

import com.jaswanth.auditlog.compliance.application.InvalidComplianceIdentityException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedComplianceIdentityResolverTest {

    private final AuthenticatedComplianceIdentityResolver resolver =
            new AuthenticatedComplianceIdentityResolver();

    @Test
    void mapsLocalAndJwtIdentitiesFromAuthenticationContext() {
        var local = resolver.resolve(UsernamePasswordAuthenticationToken.authenticated(
                "local-actor", "credential", List.of()));
        assertThat(local.actorId()).isEqualTo("local-actor");
        assertThat(local.sourceSystem()).isEqualTo("LOCAL_H2_DEMO");

        var jwt = jwt("source-application");
        var token = new JwtAuthenticationToken(jwt, List.of(), "human-actor");
        var remote = resolver.resolve(token);
        assertThat(remote.actorId()).isEqualTo("human-actor");
        assertThat(remote.sourceSystem()).isEqualTo("source-application");
    }

    @Test
    void rejectsMissingActorAndJwtSourceIdentities() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(InvalidComplianceIdentityException.class)
                .hasMessage("Authenticated actor identity is required");
        assertThatThrownBy(() -> resolver.resolve(new JwtAuthenticationToken(jwt(null), List.of(), "actor")))
                .isInstanceOf(InvalidComplianceIdentityException.class)
                .hasMessage("Authenticated source system identity is required");
    }

    private Jwt jwt(String clientId) {
        var builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("subject")
                .issuedAt(Instant.parse("2026-08-15T10:00:00Z"))
                .expiresAt(Instant.parse("2026-08-15T11:00:00Z"));
        if (clientId != null) {
            builder.claim("client_id", clientId);
        }
        return builder.build();
    }
}
