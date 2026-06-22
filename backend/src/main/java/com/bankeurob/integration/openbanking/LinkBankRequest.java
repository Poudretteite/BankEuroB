package com.bankeurob.integration.openbanking;

import lombok.Data;

@Data
public class LinkBankRequest {
    private String bankUrl;
    private String email;
    private String password;
}
