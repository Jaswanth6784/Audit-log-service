package com.jaswanth.auditlog.shared.error;

import com.jaswanth.auditlog.audit.application.InvalidAuditQueryException;
import com.jaswanth.auditlog.compliance.application.ComplianceReportScanLimitException;
import com.jaswanth.auditlog.compliance.application.InvalidComplianceIdentityException;
import com.jaswanth.auditlog.compliance.application.InvalidComplianceReportQueryException;
import com.jaswanth.auditlog.export.application.ExportTooLargeException;
import com.jaswanth.auditlog.export.application.InvalidExportScopeException;
import com.jaswanth.auditlog.redaction.application.AuditEventNotFoundException;
import com.jaswanth.auditlog.redaction.application.InvalidRedactionException;
import com.jaswanth.auditlog.redaction.application.UnsupportedRedactionException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidAuditQueryException.class)
    ProblemDetail handleInvalidAuditQuery(InvalidAuditQueryException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid audit query");
        return problem;
    }

    @ExceptionHandler(InvalidExportScopeException.class)
    ProblemDetail handleInvalidExportScope(InvalidExportScopeException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid export scope");
        return problem;
    }

    @ExceptionHandler(ExportTooLargeException.class)
    ProblemDetail handleExportTooLarge(ExportTooLargeException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, exception.getMessage());
        problem.setTitle("Audit export is too large");
        return problem;
    }

    @ExceptionHandler(InvalidComplianceIdentityException.class)
    ProblemDetail handleInvalidComplianceIdentity(InvalidComplianceIdentityException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Compliance identity is not trusted");
        return problem;
    }

    @ExceptionHandler(InvalidComplianceReportQueryException.class)
    ProblemDetail handleInvalidComplianceReportQuery(InvalidComplianceReportQueryException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid compliance report query");
        return problem;
    }

    @ExceptionHandler(ComplianceReportScanLimitException.class)
    ProblemDetail handleComplianceReportScanLimit(ComplianceReportScanLimitException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, exception.getMessage());
        problem.setTitle("Compliance report scan is too large");
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Request validation failed");
        return problem;
    }

    @ExceptionHandler(AuditEventNotFoundException.class)
    ProblemDetail handleAuditEventNotFound(AuditEventNotFoundException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Audit event not found");
        return problem;
    }

    @ExceptionHandler({InvalidRedactionException.class, UnsupportedRedactionException.class})
    ProblemDetail handleRedactionConflict(RuntimeException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Audit event cannot be redacted");
        return problem;
    }
}
