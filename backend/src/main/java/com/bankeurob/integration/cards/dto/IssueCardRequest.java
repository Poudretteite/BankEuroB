package com.bankeurob.integration.cards.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO wewnętrzne BankEuroB - bez adnotacji snake_case.
 * Używane do przyjmowania requestów z frontendu.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueCardRequest {
    private String userId;
    private String accountId;
    private String cardType;
    private double initialBalance;
}