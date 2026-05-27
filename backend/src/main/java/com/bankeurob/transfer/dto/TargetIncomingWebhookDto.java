package com.bankeurob.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class TargetIncomingWebhookDto {
    @NotBlank
    private String transactionId;
    @NotBlank
    private String senderBic;
    @NotBlank
    private String receiverIban;
    @NotNull
    private BigDecimal amount;
    @NotBlank
    private String currency;
    private String title;
}
