package com.bankeurob.transfer.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionDto {
    private UUID id;
    private String referenceNumber;
    private String transactionType;
    private String status;
    private String senderIban;
    private String senderName;
    private String receiverIban;
    private String receiverName;
    private BigDecimal amount;
    private String currency;
    private String title;
    private OffsetDateTime requestedAt;
    private OffsetDateTime completedAt;
}
