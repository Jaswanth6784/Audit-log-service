package com.jaswanth.auditlog.compliance.api;

import com.jaswanth.auditlog.compliance.application.ComplianceIdentity;
import com.jaswanth.auditlog.compliance.application.InvalidComplianceIdentityException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedComplianceIdentityResolver {

    private static final String LOCAL_SOURCE_SYSTEM = "LOCAL_H2_DEMO";

    public ComplianceIdentity resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !hasText(authentication.getName())) {
            throw new InvalidComplianceIdentityException("Authenticated actor identity is required");
        }
        var sourceSystem = authentication instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getClaim("client_id")
                : LOCAL_SOURCE_SYSTEM;
        if (!(sourceSystem instanceof String source) || !hasText(source)) {
            throw new InvalidComplianceIdentityException("Authenticated source system identity is required");
        }
        return new ComplianceIdentity(authentication.getName(), source);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
