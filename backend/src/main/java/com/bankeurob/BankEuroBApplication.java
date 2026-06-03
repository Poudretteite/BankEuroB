package com.bankeurob;

import com.bankeurob.integration.target.TargetServiceClient;
import com.bankeurob.integration.target.dto.BankCreateRequest;
import com.bankeurob.integration.target.dto.LiquidityInjectionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.math.BigDecimal;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class BankEuroBApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankEuroBApplication.class, args);
    }

    /**
     * Inicjalizacja integracji z TARGET (RTGS) przy starcie aplikacji.
     * Rejestruje BankEuroB oraz mapuje BIC-e banków odbiorców (z SWIFT)
     * jako banki w systemie TARGET, aby przelewy RTGS_TARGET2 mogły być rozliczane.
     * Zawiera mechanizm retry – czeka aż TARGET będzie dostępny (max 60s).
     */
    @Bean
    CommandLineRunner initTargetIntegration(TargetServiceClient targetClient) {
        return args -> {
            String bankBic = "BKEUDEBBXXX";
            String bankName = "BankEuroB";

            // ── Retry: czekamy aż TARGET będzie dostępny (max 12 prób co 5s = 60s) ──
            boolean targetAvailable = false;
            for (int attempt = 1; attempt <= 12; attempt++) {
                try {
                    targetClient.createBank(new BankCreateRequest(bankBic, bankName));
                    targetAvailable = true;
                    log.info("TARGET: Połączenie nawiązane (próba {}/12)", attempt);
                    break;
                } catch (Exception e) {
                    log.warn("TARGET: Próba {}/12 – serwis niedostępny ({}), retry za 5s...",
                            attempt, e.getMessage());
                    Thread.sleep(5000);
                }
            }

            if (!targetAvailable) {
                log.error("TARGET: Serwis niedostępny po 12 próbach. Inicjalizacja pominięta.");
                log.error("Sprawdź czy TARGET Service (target_service:8001) jest uruchomiony");
                log.error("i czy backend ma do niego dostęp sieciowy (docker network connect).");
                return;
            }

            // ── 1. Rejestracja BankEuroB w TARGET ──────────────────────
            try {
                targetClient.createBank(new BankCreateRequest(bankBic, bankName));
                log.info("TARGET: Zarejestrowano bank {} ({})", bankBic, bankName);
            } catch (Exception e) {
                log.warn("TARGET: Bank {} już istnieje lub błąd rejestracji: {}", bankBic, e.getMessage());
            }

            // ── 2. Wstrzyknięcie płynności dla BankEuroB ───────────────
            try {
                targetClient.injectLiquidity(new LiquidityInjectionRequest(
                        bankBic, new BigDecimal("10000000.00"), "EUR"));
                log.info("TARGET: Wstrzyknięto płynność 10 000 000 EUR dla {}", bankBic);
            } catch (Exception e) {
                log.warn("TARGET: Błąd wstrzykiwania płynności dla {}: {}", bankBic, e.getMessage());
            }

            // ── 3. Rejestracja banków odbiorców (mapowanie BIC z SWIFT) ─
            // Banki te istnieją w systemie SWIFT (mock banki na portach 3001-3006)
            // i są rejestrowane w TARGET, aby settlement międzybankowy działał.
            var receiverBanks = new String[][] {
                {"PLBKPL01XXX", "Bank Polska 1"},
                {"PLBKPL02XXX", "Bank Polska 2"},
                {"UKBKGB01XXX", "Bank UK 1"},
                {"UKBKGB02XXX", "Bank UK 2"},
                {"USBKUS01XXX", "Bank USA 1"},
                {"USBKUS02XXX", "Bank USA 2"},
            };

            for (String[] bank : receiverBanks) {
                String bic = bank[0];
                String name = bank[1];
                try {
                    targetClient.createBank(new BankCreateRequest(bic, name));
                    log.info("TARGET: Zarejestrowano bank {} ({})", bic, name);
                } catch (Exception e) {
                    log.warn("TARGET: Bank {} już istnieje lub błąd: {}", bic, e.getMessage());
                }

                try {
                    targetClient.injectLiquidity(new LiquidityInjectionRequest(
                            bic, new BigDecimal("5000000.00"), "EUR"));
                    log.info("TARGET: Wstrzyknięto płynność 5 000 000 EUR dla {}", bic);
                } catch (Exception e) {
                    log.warn("TARGET: Błąd wstrzykiwania płynności dla {}: {}", bic, e.getMessage());
                }
            }

            log.info("TARGET: Inicjalizacja integracji zakończona.");
        };
    }
}
