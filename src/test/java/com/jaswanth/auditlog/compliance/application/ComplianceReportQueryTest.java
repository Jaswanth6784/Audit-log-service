package com.jaswanth.auditlog.compliance.application;

import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceReportQueryTest {

    private static final Instant FROM = Instant.parse("2026-08-15T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-15T11:00:00Z");

    @Test
    void requiresExactlyOnePrimaryScopeAndAnIncreasingTimeRange() {
        assertThatThrownBy(() -> query(null, null, FROM, TO))
                .isInstanceOf(InvalidComplianceReportQueryException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> query("account", "actor", FROM, TO))
                .isInstanceOf(InvalidComplianceReportQueryException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> query("account", null, null, TO))
                .isInstanceOf(InvalidComplianceReportQueryException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> query("account", null, TO, TO))
                .isInstanceOf(InvalidComplianceReportQueryException.class)
                .hasMessageContaining("earlier");
        assertThatThrownBy(() -> new ComplianceReportQuery(
                "account", null, FROM, TO, null,
                null, null, null, null, 0, 50))
                .isInstanceOf(InvalidComplianceReportQueryException.class)
                .hasMessageContaining("reportPurpose");
    }

    @Test
    void describesAccountAndActorScopes() {
        var account = query("account-1", null, FROM, TO);
        assertThat(account.scopeType()).isEqualTo("ACCOUNT");
        assertThat(account.scopeValue()).isEqualTo("account-1");

        var actor = query(null, "actor-1", FROM, TO);
        assertThat(actor.scopeType()).isEqualTo("ACTOR");
        assertThat(actor.scopeValue()).isEqualTo("actor-1");
    }

    private ComplianceReportQuery query(String accountId, String actorId, Instant from, Instant to) {
        return new ComplianceReportQuery(
                accountId, actorId, from, to, AccessPurpose.REGULATORY,
                null, null, null, null, 0, 50);
    }
}
