package com.jaswanth.auditlog.redaction.api;

import com.jaswanth.auditlog.redaction.application.RedactAuditEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/audit/events/{eventId}/redactions")
@Tag(name = "Audit redaction")
public class RedactionController {

    private final RedactAuditEventService redactionService;

    public RedactionController(RedactAuditEventService redactionService) {
        this.redactionService = redactionService;
    }

    @PostMapping
    @Operation(summary = "Irreversibly redact committed payload leaf values")
    @ApiResponse(responseCode = "200", description = "Requested payload leaves were redacted")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "404", description = "Audit event was not found")
    @ApiResponse(responseCode = "409", description = "Event or payload path cannot be redacted")
    public ResponseEntity<RedactionResponse> redact(
            @PathVariable UUID eventId,
            @Valid @RequestBody RedactAuditEventRequest request) {
        return ResponseEntity.ok(RedactionResponse.from(redactionService.redact(
                eventId,
                request.paths(),
                request.actorId(),
                request.reason())));
    }
}
