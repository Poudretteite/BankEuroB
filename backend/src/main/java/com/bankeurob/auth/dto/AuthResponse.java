package com.bankeurob.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private UUID customerId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
}
