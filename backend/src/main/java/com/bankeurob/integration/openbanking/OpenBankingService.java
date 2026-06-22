package com.bankeurob.integration.openbanking;

import com.bankeurob.account.Customer;
import com.bankeurob.account.CustomerRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OpenBankingService {

    private final LinkedBankRepository linkedBankRepository;
    private final CustomerRepository customerRepository;
    private final RestTemplate restTemplate;

    public OpenBankingService(LinkedBankRepository linkedBankRepository, CustomerRepository customerRepository) {
        this.linkedBankRepository = linkedBankRepository;
        this.customerRepository = customerRepository;
        this.restTemplate = new RestTemplate();
    }

    @Transactional
    public void linkBank(String customerEmail, LinkBankRequest request) {
        Customer customer = customerRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        if (linkedBankRepository.existsByCustomerIdAndBankUrl(customer.getId(), request.getBankUrl())) {
            throw new RuntimeException("This bank is already linked");
        }

        // IMPORTANT: EuroBankA uses /auth/login
        String loginUrl = request.getBankUrl() + "/auth/login";
        Map<String, String> loginBody = new HashMap<>();
        loginBody.put("email", request.getEmail());
        loginBody.put("password", request.getPassword());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(loginUrl, loginBody, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String token = (String) response.getBody().get("token");
                LinkedBank linkedBank = LinkedBank.builder()
                        .customer(customer)
                        .bankUrl(request.getBankUrl())
                        .accessToken(token)
                        .build();
                linkedBankRepository.save(linkedBank);
            } else {
                throw new RuntimeException("Failed to authenticate with external bank");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to authenticate with external bank: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getExternalAccounts(String customerEmail) {
        Customer customer = customerRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        List<LinkedBank> linkedBanks = linkedBankRepository.findByCustomerId(customer.getId());
        List<Map<String, Object>> allExternalAccounts = new ArrayList<>();

        for (LinkedBank bank : linkedBanks) {
            try {
                String accountsUrl = bank.getBankUrl() + "/api/accounts";
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(bank.getAccessToken());
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                        accountsUrl,
                        HttpMethod.GET,
                        entity,
                        new ParameterizedTypeReference<List<Map<String, Object>>>() {}
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    for (Map<String, Object> account : response.getBody()) {
                        account.put("linkedBankId", bank.getId().toString());
                        account.put("bankUrl", bank.getBankUrl());
                        allExternalAccounts.add(account);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch accounts from " + bank.getBankUrl());
            }
        }

        return allExternalAccounts;
    }

    public Map<String, Object> executeExternalTransfer(String customerEmail, ExternalTransferRequest request) {
        LinkedBank bank = linkedBankRepository.findById(request.getLinkedBankId())
                .orElseThrow(() -> new RuntimeException("Linked bank not found"));

        if (!bank.getCustomer().getEmail().equals(customerEmail)) {
            throw new RuntimeException("Unauthorized");
        }

        String transferUrl = bank.getBankUrl() + "/api/transfers";
        
        Map<String, Object> transferBody = new HashMap<>();
        transferBody.put("fromAccountId", request.getFromAccountId());
        transferBody.put("toAccountNumber", request.getToAccountNumber());
        transferBody.put("amount", request.getAmount());
        transferBody.put("currency", request.getCurrency());
        transferBody.put("description", request.getDescription());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bank.getAccessToken());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(transferBody, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    transferUrl,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("External transfer failed");
            }
        } catch (Exception e) {
            throw new RuntimeException("External transfer failed: " + e.getMessage());
        }
    }
}
