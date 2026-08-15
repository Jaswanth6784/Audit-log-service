package com.jaswanth.auditlog.support;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TestCredentials {

    public static final String ADMIN = basic("audit-admin", "audit-admin-dev-only");
    public static final String WRITER = basic("audit-writer", "audit-writer-dev-only");
    public static final String READER = basic("audit-reader", "audit-reader-dev-only");

    private TestCredentials() {
    }

    private static String basic(String username, String password) {
        var value = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + value;
    }
}
