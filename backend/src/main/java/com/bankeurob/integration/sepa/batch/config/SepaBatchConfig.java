package com.bankeurob.integration.sepa.batch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "integration.sepa.batch")
public class SepaBatchConfig {
    private String baseUrl = "http://localhost:8002";
    private int connectTimeout = 5000;
    private int readTimeout = 30000;
}
