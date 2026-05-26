package com.bankeurob.integration.target.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "integration.target")
public class TargetServiceConfig {
    private String baseUrl = "http://localhost:8001";
    private int connectTimeout = 5000;
    private int readTimeout = 10000;
}
