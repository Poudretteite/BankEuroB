package com.bankeurob.integration.swift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "integration.swift")
public class SwiftServiceConfig {
    private String baseUrl = "http://localhost:3000";
    private String clientId = "test-client";
    private String clientSecret = "test-secret";
    private int connectTimeout = 5000;
    private int readTimeout = 15000;
}
