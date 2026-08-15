package com.jaswanth.auditlog.shared.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;

@Profile("h2")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocalSecurityProperties.class)
public class LocalAuthenticationConfiguration {

    @Bean
    PasswordEncoder localPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService localUsers(LocalSecurityProperties properties, PasswordEncoder passwordEncoder) {
        var admin = User.builder()
                .username("audit-admin")
                .password(passwordEncoder.encode(properties.adminPassword()))
                .authorities(
                        AuditAuthority.WRITE,
                        AuditAuthority.READ,
                        AuditAuthority.VERIFY,
                        AuditAuthority.EXPORT,
                        AuditAuthority.PRIVACY_ADMIN,
                        AuditAuthority.RETENTION_ADMIN,
                        AuditAuthority.MONITOR)
                .build();
        var writer = User.builder()
                .username("audit-writer")
                .password(passwordEncoder.encode(properties.writerPassword()))
                .authorities(AuditAuthority.WRITE)
                .build();
        var reader = User.builder()
                .username("audit-reader")
                .password(passwordEncoder.encode(properties.readerPassword()))
                .authorities(AuditAuthority.READ, AuditAuthority.VERIFY)
                .build();
        return new InMemoryUserDetailsManager(admin, writer, reader);
    }

    @Bean
    AuthenticationEntryPoint localAuthenticationEntryPoint(SecurityProblemWriter problemWriter) {
        return new JsonAuthenticationEntryPoint(problemWriter, "Basic realm=\"audit-log-service\"");
    }

    @Bean
    AuditAuthenticationConfigurer localAuthenticationConfigurer(AuthenticationEntryPoint entryPoint) {
        return http -> http.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint));
    }
}
