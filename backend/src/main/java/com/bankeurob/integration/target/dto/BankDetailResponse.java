package com.bankeurob.integration.target.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class BankDetailResponse {
    private Integer id;
    private String bic;
    private String name;

    @JsonProperty("is_blocked")
    private Boolean isBlocked;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("settlement_accounts")
    private List<SettlementAccountResponse> settlementAccounts;
}
