package com.jaswanth.auditlog.shared.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain auditSecurityFilterChain(
            HttpSecurity http,
            AuditAuthenticationConfigurer authenticationConfigurer,
            AuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/audit/events/*/redactions")
                        .hasAuthority(AuditAuthority.PRIVACY_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/compliance/access-events")
                        .hasAuthority(AuditAuthority.COMPLIANCE_ACCESS_WRITE)
                        .requestMatchers(HttpMethod.GET, "/compliance/access-reports")
                        .hasAuthority(AuditAuthority.COMPLIANCE_REPORT_READ)
                        .requestMatchers(HttpMethod.GET, "/compliance/access-exports")
                        .hasAuthority(AuditAuthority.COMPLIANCE_REPORT_EXPORT)
                        .requestMatchers(HttpMethod.POST, "/compliance/access-exports/verification")
                        .hasAuthority(AuditAuthority.VERIFY)
                        .requestMatchers(HttpMethod.POST, "/audit/events")
                        .hasAuthority(AuditAuthority.WRITE)
                        .requestMatchers(HttpMethod.GET, "/audit/events")
                        .hasAuthority(AuditAuthority.READ)
                        .requestMatchers(HttpMethod.GET, "/audit/verify", "/audit/verification")
                        .hasAuthority(AuditAuthority.VERIFY)
                        .requestMatchers(HttpMethod.GET, "/audit/exports")
                        .hasAuthority(AuditAuthority.EXPORT)
                        .requestMatchers(HttpMethod.POST, "/audit/exports/verification")
                        .hasAuthority(AuditAuthority.VERIFY)
                        .requestMatchers(HttpMethod.POST, "/audit/retention/runs")
                        .hasAuthority(AuditAuthority.RETENTION_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                        .hasAuthority(AuditAuthority.MONITOR)
                        .requestMatchers(HttpMethod.GET,
                                "/api-docs", "/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .hasAuthority(AuditAuthority.READ)
                        .anyRequest().denyAll());
        authenticationConfigurer.configure(http);
        return http.build();
    }
}
