package com.bankeurob.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email jest wymagany")
    @Email(message = "Podaj prawidłowy adres email")
    private String email;

    @NotBlank(message = "Hasło jest wymagane")
    @Size(min = 8, max = 100, message = "Hasło musi mieć od 8 do 100 znaków")
    private String password;

    @NotBlank(message = "Imię jest wymagane")
    private String firstName;

    @NotBlank(message = "Nazwisko jest wymagane")
    private String lastName;

    @NotNull(message = "Data urodzenia jest wymagana")
    @Past(message = "Data urodzenia musi być w przeszłości")
    private LocalDate dateOfBirth;

    private String phone;
    private String addressStreet;
    private String addressCity;
}
