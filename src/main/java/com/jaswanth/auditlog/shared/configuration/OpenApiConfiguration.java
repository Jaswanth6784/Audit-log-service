package com.jaswanth.auditlog.shared.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI auditLogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Audit Log Service API")
                .description("Tamper-evident, append-only audit event API")
                .version("v1")
                .contact(new Contact().name("Audit Log Service Team")));
    }
}
