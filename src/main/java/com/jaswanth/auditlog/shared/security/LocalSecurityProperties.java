package com.jaswanth.auditlog.shared.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "audit.security.local")
public record LocalSecurityProperties(
        @NotBlank @Size(min = 12) String adminPassword,
        @NotBlank @Size(min = 12) String writerPassword,
        @NotBlank @Size(min = 12) String readerPassword) {
}
