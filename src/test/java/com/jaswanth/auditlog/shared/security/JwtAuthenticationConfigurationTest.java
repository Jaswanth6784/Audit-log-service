package com.jaswanth.auditlog.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationConfigurationTest {

    @Test
    void mapsExplicitRolesClaimWithoutAddingAPrefix() {
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("audit-user")
                .issuedAt(Instant.parse("2026-08-15T10:00:00Z"))
                .expiresAt(Instant.parse("2026-08-15T11:00:00Z"))
                .claim("roles", List.of(AuditAuthority.READ, AuditAuthority.VERIFY))
                .build();

        var authentication = new JwtAuthenticationConfiguration()
                .auditJwtAuthenticationConverter()
                .convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("audit-user");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains(AuditAuthority.READ, AuditAuthority.VERIFY);
    }
}
