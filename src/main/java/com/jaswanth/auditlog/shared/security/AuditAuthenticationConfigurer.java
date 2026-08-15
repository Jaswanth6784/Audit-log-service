package com.jaswanth.auditlog.shared.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@FunctionalInterface
public interface AuditAuthenticationConfigurer {

    void configure(HttpSecurity http) throws Exception;
}
