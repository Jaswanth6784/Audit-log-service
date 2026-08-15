package com.jaswanth.auditlog.audit.api;

import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/audit/events")
@Tag(name = "Audit events")
public class AuditEventController {

    private final AppendAuditEventService appendService;

    public AuditEventController(AppendAuditEventService appendService) {
        this.appendService = appendService;
    }

    @PostMapping
    @Operation(summary = "Append an immutable audit event")
    @ApiResponse(responseCode = "201", description = "Event appended to the hash chain")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    public ResponseEntity<AppendAuditEventResponse> append(@Valid @RequestBody AppendAuditEventRequest request) {
        var response = AppendAuditEventResponse.from(appendService.append(request.toCommand()));
        return ResponseEntity.created(URI.create("/audit/events/" + response.eventId())).body(response);
    }
}
