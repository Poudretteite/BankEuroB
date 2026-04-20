package com.bankeurob.transfer.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferRequest {

    @NotBlank(message = "IBAN nadawcy jest wymagany")
    private String senderIban;

    @NotBlank(message = "IBAN odbiorcy jest wymagany")
    private String receiverIban;

    @NotBlank(message = "Nazwa odbiorcy jest wymagana")
    private String receiverName;

    @NotNull(message = "Kwota jest wymagana")
    @Positive(message = "Kwota musi być dodatnia")
    @DecimalMax(value = "100000.00", message = "Kwota nie może przekraczać 100 000 EUR")
    private BigDecimal amount;

    @Size(max = 140, message = "Tytuł może mieć maksymalnie 140 znaków")
    private String title;

    // INTERNAL, SEPA_SCT, SEPA_INSTANT, SWIFT
    private String transferType = "INTERNAL";
}
