package com.jaswanth.auditlog.compliance.export.application;

import com.jaswanth.auditlog.compliance.application.InvalidComplianceReportQueryException;
import com.jaswanth.auditlog.compliance.application.RecordComplianceAccessService;
import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;
import com.jaswanth.auditlog.export.model.AuditExportRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceExportScopeTest {

    private static final Instant FROM = Instant.parse("2026-08-15T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-15T11:00:00Z");

    @Test
    void validatesScopeTimePurposeAndSource() {
        assertThatThrownBy(() -> scope(null, null, FROM, TO, AccessPurpose.REGULATORY, null))
                .isInstanceOf(InvalidComplianceReportQueryException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> scope("account", "actor", FROM, TO, AccessPurpose.REGULATORY, null))
                .isInstanceOf(InvalidComplianceReportQueryException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> scope("account", null, null, TO, AccessPurpose.REGULATORY, null))
                .isInstanceOf(InvalidComplianceReportQueryException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> scope("account", null, TO, TO, AccessPurpose.REGULATORY, null))
                .isInstanceOf(InvalidComplianceReportQueryException.class)
                .hasMessageContaining("earlier");
        assertThatThrownBy(() -> scope("account", null, FROM, TO, null, null))
                .isInstanceOf(InvalidComplianceReportQueryException.class)
                .hasMessageContaining("reportPurpose");
        assertThatThrownBy(() -> scope("account", null, FROM, TO, AccessPurpose.REGULATORY, " "))
                .isInstanceOf(InvalidComplianceReportQueryException.class)
                .hasMessageContaining("sourceSystem");
    }

    @Test
    void appliesTypedScopeTimeAndPayloadCriteria() {
        var scope = new ComplianceExportScope(
                "account-a", null, FROM, TO, AccessPurpose.REGULATORY,
                AccessAction.VIEW, AccessOutcome.ALLOWED, "source-a", ClientDataCategory.BALANCES);
        assertThat(scope.matches(record(
                "actor-1", "account-a", FROM.plusSeconds(60),
                Map.of(
                        "action", "VIEW",
                        "outcome", "ALLOWED",
                        "sourceSystem", "source-a",
                        "dataCategories", List.of("BALANCES")))))
                .isTrue();
        assertThat(scope.matches(record(
                "actor-1", "account-a", TO,
                Map.of(
                        "action", "VIEW",
                        "outcome", "ALLOWED",
                        "sourceSystem", "source-a",
                        "dataCategories", List.of("BALANCES")))))
                .isFalse();
        assertThat(scope.matches(record(
                "actor-1", "account-b", FROM.plusSeconds(60),
                Map.of(
                        "action", "VIEW",
                        "outcome", "ALLOWED",
                        "sourceSystem", "source-a",
                        "dataCategories", List.of("BALANCES")))))
                .isFalse();

        var actorScope = new ComplianceExportScope(
                null, "actor-1", FROM, TO, AccessPurpose.SECURITY_INVESTIGATION,
                null, null, null, null);
        assertThat(actorScope.matches(record(
                "actor-1", "account-b", FROM.plusSeconds(60), Map.of())))
                .isTrue();
    }

    private ComplianceExportScope scope(
            String account,
            String actor,
            Instant from,
            Instant to,
            AccessPurpose purpose,
            String source) {
        return new ComplianceExportScope(
                account, actor, from, to, purpose, null, null, source, null);
    }

    private AuditExportRecord record(
            String actor,
            String account,
            Instant timestamp,
            Map<String, Object> payload) {
        return new AuditExportRecord(
                AuditExportRecord.FULL,
                1,
                null,
                RecordComplianceAccessService.EVENT_TYPE,
                actor,
                RecordComplianceAccessService.RESOURCE_TYPE,
                account,
                payload,
                null,
                timestamp,
                null,
                (short) 2,
                null,
                null,
                null);
    }
}
