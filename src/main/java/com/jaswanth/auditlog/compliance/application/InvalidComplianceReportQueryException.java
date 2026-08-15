package com.jaswanth.auditlog.compliance.application;

public class InvalidComplianceReportQueryException extends RuntimeException {

    public InvalidComplianceReportQueryException(String message) {
        super(message);
    }
}
