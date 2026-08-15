package com.jaswanth.auditlog.compliance.api;

import com.jaswanth.auditlog.compliance.application.ComplianceReportQuery;
import com.jaswanth.auditlog.compliance.application.InvalidComplianceIdentityException;
import com.jaswanth.auditlog.compliance.application.QueryComplianceReportService;
import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/compliance/access-reports")
@Tag(name = "Compliance access reports")
@Validated
public class ComplianceReportController {

    private final QueryComplianceReportService queryService;

    public ComplianceReportController(QueryComplianceReportService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Query a bounded, minimized report of recorded client-account-data access")
    @ApiResponse(responseCode = "200", description = "Sequence-ordered report page and access receipt")
    @ApiResponse(responseCode = "400", description = "Scope, time range, filter, or pagination is invalid")
    @ApiResponse(responseCode = "401", description = "Authentication is required")
    @ApiResponse(responseCode = "403", description = "Compliance report authority is required")
    @ApiResponse(responseCode = "413", description = "Criteria require an excessive candidate scan")
    public ComplianceReportPageResponse query(
            Authentication authentication,
            @RequestParam(required = false)
            @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String accountId,
            @RequestParam(required = false)
            @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String actorId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam AccessPurpose reportPurpose,
            @RequestParam(required = false) AccessAction action,
            @RequestParam(required = false) AccessOutcome outcome,
            @RequestParam(required = false)
            @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String sourceSystem,
            @RequestParam(required = false) ClientDataCategory dataCategory,
            @Parameter(description = "Return records after this immutable global sequence cursor")
            @RequestParam(defaultValue = "0") @Min(0) long afterSequence,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new InvalidComplianceIdentityException("Authenticated report consumer identity is required");
        }
        var query = new ComplianceReportQuery(
                accountId,
                actorId,
                from,
                to,
                reportPurpose,
                action,
                outcome,
                sourceSystem,
                dataCategory,
                afterSequence,
                limit);
        return ComplianceReportPageResponse.from(queryService.query(authentication.getName(), query));
    }
}
