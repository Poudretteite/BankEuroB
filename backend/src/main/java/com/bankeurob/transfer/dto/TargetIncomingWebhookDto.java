package com.bankeurob.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

@Data
public class TargetIncomingWebhookDto {

    @NotBlank
    @JsonProperty("event")
    private String event;

    @NotBlank
    @JsonProperty("transfer_id")
    private String transferId;

    @NotBlank
    @JsonProperty("sender_bic")
    private String senderBic;

    @NotBlank
    @JsonProperty("receiver_bic")
    private String receiverBic;

    @JsonProperty("sender_iban")
    private String senderIban;

    @JsonProperty("receiver_iban")
    private String receiverIban;

    @NotNull
    @JsonProperty("amount")
    private BigDecimal amount;

    @NotBlank
    @JsonProperty("currency")
    private String currency;

    @JsonProperty("description")
    private String description;

    @JsonProperty("settled_at")
    private OffsetDateTime settledAt;

    @JsonProperty("signature")
    private String signature;
}
