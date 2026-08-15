package com.jaswanth.auditlog.audit.api;

import com.jaswanth.auditlog.audit.application.VerifyAuditChainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/audit/verify", "/audit/verification"})
@Tag(name = "Audit verification")
public class AuditVerificationController {

    private final VerifyAuditChainService verificationService;

    public AuditVerificationController(VerifyAuditChainService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping
    @Operation(summary = "Verify the complete audit hash chain")
    @ApiResponse(responseCode = "200", description = "Verification completed; inspect the valid field")
    public AuditVerificationResponse verify() {
        return AuditVerificationResponse.from(verificationService.verify());
    }
}
