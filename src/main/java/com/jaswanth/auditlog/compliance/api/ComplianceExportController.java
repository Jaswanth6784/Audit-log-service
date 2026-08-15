package com.jaswanth.auditlog.compliance.api;

import com.jaswanth.auditlog.compliance.application.InvalidComplianceIdentityException;
import com.jaswanth.auditlog.compliance.domain.AccessAction;
import com.jaswanth.auditlog.compliance.domain.AccessOutcome;
import com.jaswanth.auditlog.compliance.domain.AccessPurpose;
import com.jaswanth.auditlog.compliance.domain.ClientDataCategory;
import com.jaswanth.auditlog.compliance.export.application.ComplianceExportScope;
import com.jaswanth.auditlog.compliance.export.application.CreateComplianceExportService;
import com.jaswanth.auditlog.compliance.export.application.VerifyComplianceExportService;
import com.jaswanth.auditlog.compliance.export.model.ComplianceExportBundle;
import com.jaswanth.auditlog.export.api.AuditExportVerificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Validated
@RestController
@RequestMapping("/compliance/access-exports")
@Tag(name = "Compliance access exports")
public class ComplianceExportController {

    private final CreateComplianceExportService createService;
    private final VerifyComplianceExportService verificationService;

    public ComplianceExportController(
            CreateComplianceExportService createService,
            VerifyComplianceExportService verificationService) {
        this.createService = createService;
        this.verificationService = verificationService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create signed compliance evidence bound to normalized report criteria")
    @ApiResponse(responseCode = "200", description = "Signed compliance export bundle")
    @ApiResponse(responseCode = "400", description = "Scope, purpose, time range, or filter is invalid")
    @ApiResponse(responseCode = "401", description = "Authentication is required")
    @ApiResponse(responseCode = "403", description = "Compliance export authority is required")
    @ApiResponse(responseCode = "413", description = "Chain exceeds the synchronous export limit")
    public ResponseEntity<ComplianceExportBundle> create(
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
            @RequestParam(required = false) ClientDataCategory dataCategory) {
        var consumerActorId = authenticatedName(authentication);
        var bundle = createService.create(consumerActorId, new ComplianceExportScope(
                accountId,
                actorId,
                from,
                to,
                reportPurpose,
                action,
                outcome,
                sourceSystem,
                dataCategory));
        var disposition = ContentDisposition.attachment()
                .filename("compliance-access-export-" + bundle.manifest().bundleId() + ".json")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bundle);
    }

    @PostMapping(path = "/verification", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verify compliance criteria, signature, disclosed events, and global chain")
    @ApiResponse(responseCode = "200", description = "Verification completed; inspect the valid field")
    @ApiResponse(responseCode = "401", description = "Authentication is required")
    @ApiResponse(responseCode = "403", description = "Audit verification authority is required")
    public AuditExportVerificationResponse verify(@Valid @RequestBody ComplianceExportBundle bundle) {
        return AuditExportVerificationResponse.from(verificationService.verify(bundle));
    }

    private String authenticatedName(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new InvalidComplianceIdentityException("Authenticated export consumer identity is required");
        }
        return authentication.getName();
    }
}
