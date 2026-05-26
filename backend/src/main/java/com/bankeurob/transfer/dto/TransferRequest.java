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

    /** BIC/SWIFT banku odbiorcy – wymagane dla przelewów SEPA i SWIFT */
    private String receiverBic;

    @NotNull(message = "Kwota jest wymagana")
    @Positive(message = "Kwota musi być dodatnia")
    @DecimalMax(value = "100000.00", message = "Kwota nie może przekraczać 100 000 EUR")
    private BigDecimal amount;

    @Size(max = 140, message = "Tytuł może mieć maksymalnie 140 znaków")
    private String title;

    // INTERNAL, SEPA_SCT, SEPA_INSTANT, SWIFT
    private String transferType = "INTERNAL";

    /**
     * Charge Bearer (ChrgBr) – określa kto ponosi opłaty za przelew SWIFT.
     * <ul>
     *   <li>DEBT – płaci nadawca (debitor)</li>
     *   <li>CRED – płaci odbiorca (kredytor)</li>
     *   <li>SHAR – koszty dzielone (domyślnie)</li>
     *   <li>SLEV – zasady opłat ustalane przez schemat usługi</li>
     * </ul>
     * Pole wykorzystywane tylko dla przelewów typu SWIFT.
     */
    private String chargeBearer = "SHAR";
}
