package com.jaswanth.auditlog.export.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExportProperties.class)
public class ExportConfiguration {
}
