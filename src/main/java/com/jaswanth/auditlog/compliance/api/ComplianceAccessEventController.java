package com.jaswanth.auditlog.compliance.api;

import com.jaswanth.auditlog.compliance.application.RecordComplianceAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/compliance/access-events")
@Tag(name = "Compliance access evidence")
public class ComplianceAccessEventController {

    private final RecordComplianceAccessService recordService;
    private final AuthenticatedComplianceIdentityResolver identityResolver;

    public ComplianceAccessEventController(
            RecordComplianceAccessService recordService,
            AuthenticatedComplianceIdentityResolver identityResolver) {
        this.recordService = recordService;
        this.identityResolver = identityResolver;
    }

    @PostMapping
    @Operation(summary = "Record a normalized client-account data access attempt")
    @ApiResponse(responseCode = "201", description = "Compliance access evidence appended")
    @ApiResponse(responseCode = "400", description = "Request schema or controlled value is invalid")
    @ApiResponse(responseCode = "401", description = "Authentication is required")
    @ApiResponse(responseCode = "403", description = "Authority or trusted source identity is missing")
    public ResponseEntity<ComplianceAccessEventResponse> record(
            Authentication authentication,
            @Valid @RequestBody RecordComplianceAccessRequest request) {
        var identity = identityResolver.resolve(authentication);
        var response = ComplianceAccessEventResponse.from(recordService.record(request.toCommand(identity)));
        return ResponseEntity
                .created(URI.create("/compliance/access-events/" + response.eventId()))
                .body(response);
    }
}
