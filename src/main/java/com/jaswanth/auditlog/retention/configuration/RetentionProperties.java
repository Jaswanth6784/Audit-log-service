package com.jaswanth.auditlog.retention.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "audit.retention")
public record RetentionProperties(
        @DefaultValue("365d") Duration maxAge,
        @DefaultValue("500") @Min(1) @Max(10_000) int batchSize) {

    public RetentionProperties {
        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("audit.retention.max-age must be positive");
        }
    }
}
