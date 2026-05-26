package com.bankeurob.integration.sepa.instant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "integration.sepa.instant")
public class SepaInstantConfig {
    private String baseUrl = "http://localhost:8003";
    private int connectTimeout = 5000;
    private int readTimeout = 10000;
}
