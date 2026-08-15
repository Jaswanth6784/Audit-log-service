package com.jaswanth.auditlog.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;

@Profile("postgres")
@Configuration(proxyBeanMethods = false)
public class JwtAuthenticationConfiguration {

    @Bean
    AuthenticationEntryPoint jwtAuthenticationEntryPoint(SecurityProblemWriter problemWriter) {
        return new JsonAuthenticationEntryPoint(problemWriter, "Bearer");
    }

    @Bean
    JwtAuthenticationConverter auditJwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    AuditAuthenticationConfigurer jwtAuthenticationConfigurer(
            AuthenticationEntryPoint entryPoint,
            JwtAuthenticationConverter jwtAuthenticationConverter) {
        return http -> http.oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(entryPoint)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
    }
}
