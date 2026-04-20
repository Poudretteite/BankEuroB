package com.bankeurob.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email jest wymagany")
    @Email(message = "Podaj prawidłowy adres email")
    private String email;

    @NotBlank(message = "Hasło jest wymagane")
    private String password;
}
