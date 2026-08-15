package com.jaswanth.auditlog.redaction.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RedactAuditEventRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotEmpty @Size(max = 100)
        List<@NotBlank @Size(max = 500)
                @Pattern(regexp = "/.*", message = "must be an RFC 6901 JSON Pointer") String> paths) {
}
