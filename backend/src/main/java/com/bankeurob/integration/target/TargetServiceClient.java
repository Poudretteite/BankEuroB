package com.bankeurob.integration.target;

import com.bankeurob.integration.target.config.TargetServiceConfig;
import com.bankeurob.integration.target.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Klient HTTP do komunikacji z TARGET Service (Central Bank RTGS).
 * 
 * Endpointy:
 * - GET/POST /banks         – rejestracja i lista banków
 * - GET /banks/{bic}        – szczegóły banku
 * - POST /banks/block/{bic} – blokada banku
 * - POST /banks/unblock/{bic} – odblokowanie banku
 * - POST /settle/payment    – rozliczenie płatności międzybankowej
 * - POST /liquidity/injection – zastrzyk płynności
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TargetServiceClient {

    private final RestTemplate restTemplate;
    private final TargetServiceConfig config;

    // ─────────────────────────────────────────────────
    // Banks
    // ─────────────────────────────────────────────────

    public List<BankResponse> getBanks() {
        String url = config.getBaseUrl() + "/banks";
        ResponseEntity<List<BankResponse>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public BankResponse createBank(BankCreateRequest request) {
        String url = config.getBaseUrl() + "/banks";
        ResponseEntity<BankResponse> response = restTemplate.postForEntity(url, request, BankResponse.class);
        return response.getBody();
    }

    public BankDetailResponse getBank(String bic) {
        String url = config.getBaseUrl() + "/banks/" + bic;
        ResponseEntity<BankDetailResponse> response = restTemplate.getForEntity(url, BankDetailResponse.class);
        return response.getBody();
    }

    public void blockBank(String bic) {
        String url = config.getBaseUrl() + "/banks/block/" + bic;
        restTemplate.postForEntity(url, null, Void.class);
    }

    public void unblockBank(String bic) {
        String url = config.getBaseUrl() + "/banks/unblock/" + bic;
        restTemplate.postForEntity(url, null, Void.class);
    }

    // ─────────────────────────────────────────────────
    // Settlement
    // ─────────────────────────────────────────────────

    public SettlementResponse settlePayment(SettlementRequest request) {
        String url = config.getBaseUrl() + "/settle/payment";
        log.info("TARGET settlement: sender={} receiver={} amount={}",
                request.getSenderBic(), request.getReceiverBic(), request.getAmount());
        try {
            ResponseEntity<SettlementResponse> response = restTemplate.postForEntity(url, request, SettlementResponse.class);
            log.info("TARGET settlement response: status={}", response.getBody().getStatus());
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("TARGET Service unavailable: {}", e.getMessage());
            throw new RuntimeException("TARGET Service (RTGS) jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("TARGET settlement error: {}", e.getMessage());
            throw new RuntimeException("Błąd settlement w TARGET: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // Liquidity
    // ─────────────────────────────────────────────────

    public LiquidityInjectionResponse injectLiquidity(LiquidityInjectionRequest request) {
        String url = config.getBaseUrl() + "/liquidity/injection";
        ResponseEntity<LiquidityInjectionResponse> response = restTemplate.postForEntity(url, request, LiquidityInjectionResponse.class);
        return response.getBody();
    }
}
