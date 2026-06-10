package com.bankeurob.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Konfiguracja RestTemplate dla klientów integracyjnych.
 * Ustawia timeouty i inne parametry połączeń HTTP do zewnętrznych serwisów.
 */
@Configuration
public class IntegrationRestTemplateConfig {

    @Bean
    public RestTemplate integrationRestTemplate() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new RestTemplate(factory);
    }
}
