package com.bankeurob.account.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class JuniorAccountRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private LocalDate dateOfBirth;
    private String pesel;
    private String phone;
    private String addressStreet;
    private String addressCity;
    private String addressCountry;
}
