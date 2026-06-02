package com.bankeurob.integration.klik.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguracja integracji z systemem KLIK (płatności mobilne).
 *
 * @see <a href="https://github.com/your-org/KLIK-payments">KLIK Payments</a>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "integration.klik")
public class KlikConfig {
    /** Bazowy URL API KLIK (domyślnie localhost:8000). */
    private String baseUrl = "http://localhost:8000";

    /** Klucz API BankEuroB wydany przez operatora KLIK. */
    private String apiKey = "klik_776d015433da425cae6d89576e3cc416";

    /** Timeout połączenia w ms. */
    private int connectTimeout = 5000;

    /** Timeout odczytu w ms. */
    private int readTimeout = 10000;
}
