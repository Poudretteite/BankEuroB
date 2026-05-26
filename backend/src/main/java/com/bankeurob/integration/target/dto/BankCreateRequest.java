package com.bankeurob.integration.target.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankCreateRequest {
    private String bic;
    private String name;
}
