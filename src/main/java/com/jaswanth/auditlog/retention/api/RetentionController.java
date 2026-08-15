package com.jaswanth.auditlog.retention.api;

import com.jaswanth.auditlog.retention.application.ApplyRetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit/retention/runs")
@Tag(name = "Audit retention")
public class RetentionController {

    private final ApplyRetentionService retentionService;

    public RetentionController(ApplyRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @PostMapping
    @Operation(summary = "Archive the next bounded batch of expired audit events")
    @ApiResponse(responseCode = "200", description = "Retention batch completed")
    public RetentionRunResponse run() {
        return RetentionRunResponse.from(retentionService.archiveNextBatch());
    }
}
