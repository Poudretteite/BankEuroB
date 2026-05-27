package com.bankeurob.integration.target.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class BankResponse {
    private Integer id;
    private String bic;
    private String name;

    @JsonProperty("is_blocked")
    private Boolean isBlocked;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
