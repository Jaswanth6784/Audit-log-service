package com.jaswanth.auditlog.export.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "audit.export")
public record ExportProperties(
        @NotBlank String keyId,
        @NotBlank String privateKey,
        @NotBlank String publicKey,
        @Min(1) @Max(1_000_000) int maxChainEvents) {
}
