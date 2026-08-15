package com.jaswanth.auditlog.audit.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventQueryTest {

    @Test
    void rejectsAnEmptyOrReversedTimeRange() {
        var boundary = Instant.parse("2026-08-15T10:00:00Z");

        assertThatThrownBy(() -> query(boundary, boundary))
                .isInstanceOf(InvalidAuditQueryException.class)
                .hasMessageContaining("earlier");
        assertThatThrownBy(() -> query(boundary.plusSeconds(1), boundary))
                .isInstanceOf(InvalidAuditQueryException.class)
                .hasMessageContaining("earlier");
    }

    private AuditEventQuery query(Instant from, Instant to) {
        return new AuditEventQuery(null, null, null, null, from, to, 0, 50);
    }
}
