package com.jaswanth.auditlog.retention.configuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetentionPropertiesTest {

    @Test
    void rejectsNonPositiveRetentionAge() {
        assertThatThrownBy(() -> new RetentionProperties(Duration.ZERO, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-age must be positive");
        assertThatThrownBy(() -> new RetentionProperties(Duration.ofDays(-1), 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-age must be positive");
    }
}
