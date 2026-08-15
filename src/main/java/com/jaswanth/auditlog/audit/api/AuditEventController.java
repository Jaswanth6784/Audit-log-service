package com.jaswanth.auditlog.audit.api;

import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
import com.jaswanth.auditlog.audit.application.AuditEventQuery;
import com.jaswanth.auditlog.audit.application.QueryAuditEventsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/audit/events")
@Tag(name = "Audit events")
@Validated
public class AuditEventController {

    private final AppendAuditEventService appendService;
    private final QueryAuditEventsService queryService;

    public AuditEventController(AppendAuditEventService appendService, QueryAuditEventsService queryService) {
        this.appendService = appendService;
        this.queryService = queryService;
    }

    @PostMapping
    @Operation(summary = "Append an immutable audit event")
    @ApiResponse(responseCode = "201", description = "Event appended to the hash chain")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    public ResponseEntity<AuditEventResponse> append(@Valid @RequestBody AppendAuditEventRequest request) {
        var response = AuditEventResponse.from(appendService.append(request.toCommand()));
        return ResponseEntity.created(URI.create("/audit/events/" + response.eventId())).body(response);
    }

    @GetMapping
    @Operation(summary = "Query audit events using stable keyset pagination")
    @ApiResponse(responseCode = "200", description = "A sequence-ordered page of events")
    @ApiResponse(responseCode = "400", description = "Filter or pagination validation failed")
    public AuditEventPageResponse query(
            @RequestParam(required = false)
            @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String actorId,
            @RequestParam(required = false)
            @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String resourceType,
            @RequestParam(required = false)
            @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String resourceId,
            @RequestParam(required = false)
            @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String eventType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @Parameter(description = "Return records with a sequence number greater than this cursor")
            @RequestParam(defaultValue = "0") @Min(0) long afterSequence,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        var query = new AuditEventQuery(
                actorId,
                resourceType,
                resourceId,
                eventType,
                from,
                to,
                afterSequence,
                limit);
        return AuditEventPageResponse.from(queryService.query(query));
    }
}
