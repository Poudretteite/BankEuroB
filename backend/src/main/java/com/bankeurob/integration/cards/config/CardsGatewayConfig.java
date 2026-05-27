package com.bankeurob.integration.cards.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "integration.cards.gateway")
public class CardsGatewayConfig {
    private String baseUrl = "http://localhost:8072";
    private int connectTimeout = 5000;
    private int readTimeout = 10000;
}
