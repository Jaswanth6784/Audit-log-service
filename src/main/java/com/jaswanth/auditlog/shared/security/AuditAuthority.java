package com.jaswanth.auditlog.shared.security;

public final class AuditAuthority {

    public static final String WRITE = "AUDIT_WRITER";
    public static final String READ = "AUDIT_READER";
    public static final String VERIFY = "AUDIT_VERIFIER";
    public static final String EXPORT = "AUDIT_EXPORTER";
    public static final String PRIVACY_ADMIN = "AUDIT_PRIVACY_ADMIN";
    public static final String RETENTION_ADMIN = "AUDIT_RETENTION_ADMIN";
    public static final String MONITOR = "AUDIT_MONITOR";
    public static final String COMPLIANCE_ACCESS_WRITE = "COMPLIANCE_ACCESS_WRITE";

    private AuditAuthority() {
    }
}
