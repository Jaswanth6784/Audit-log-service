package com.jaswanth.auditlog.export.api;

import com.jaswanth.auditlog.export.application.CreateAuditExportService;
import com.jaswanth.auditlog.export.application.ExportScope;
import com.jaswanth.auditlog.export.application.VerifyAuditExportService;
import com.jaswanth.auditlog.export.model.AuditExportBundle;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/audit/exports")
@Tag(name = "Audit exports")
public class AuditExportController {

    private final CreateAuditExportService createService;
    private final VerifyAuditExportService verificationService;

    public AuditExportController(
            CreateAuditExportService createService,
            VerifyAuditExportService verificationService) {
        this.createService = createService;
        this.verificationService = verificationService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a signed, independently verifiable scoped audit export")
    @ApiResponse(responseCode = "200", description = "Signed export bundle")
    @ApiResponse(responseCode = "400", description = "Scope is missing or ambiguous")
    @ApiResponse(responseCode = "413", description = "Chain exceeds the synchronous export limit")
    public ResponseEntity<AuditExportBundle> create(
            @Parameter(description = "Export all events for this actor")
            @RequestParam(required = false) @Size(max = 255) String actorId,
            @Parameter(description = "Resource type; must be paired with resourceId")
            @RequestParam(required = false) @Size(max = 100) String resourceType,
            @Parameter(description = "Resource ID; must be paired with resourceType")
            @RequestParam(required = false) @Size(max = 255) String resourceId) {
        var bundle = createService.create(new ExportScope(actorId, resourceType, resourceId));
        var disposition = ContentDisposition.attachment()
                .filename("audit-export-" + bundle.manifest().bundleId() + ".json")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bundle);
    }

    @PostMapping(path = "/verification", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verify the signature, scope, commitments, and hash chain of an export")
    @ApiResponse(responseCode = "200", description = "Verification completed; inspect the valid field")
    public AuditExportVerificationResponse verify(@Valid @RequestBody AuditExportBundle bundle) {
        return AuditExportVerificationResponse.from(verificationService.verify(bundle));
    }
}
