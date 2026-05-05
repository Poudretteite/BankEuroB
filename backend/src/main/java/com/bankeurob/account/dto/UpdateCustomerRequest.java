package com.bankeurob.account.dto;

import lombok.Data;

@Data
public class UpdateCustomerRequest {
    private String phone;
    private String addressStreet;
    private String addressCity;
    private String addressCountry;
}
