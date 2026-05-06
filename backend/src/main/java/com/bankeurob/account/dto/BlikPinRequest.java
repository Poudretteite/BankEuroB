package com.bankeurob.account.dto;

import lombok.Data;

@Data
public class BlikPinRequest {
    private String currentPin;
    private String newPin;
}
