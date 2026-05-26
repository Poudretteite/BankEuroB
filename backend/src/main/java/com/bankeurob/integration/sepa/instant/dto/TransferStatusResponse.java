package com.bankeurob.integration.sepa.instant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
public class TransferStatusResponse {

    @JsonProperty("transfer_id")
    private String transferId;

    private String status;

    @JsonProperty("processed_at")
    private OffsetDateTime processedAt;

    @JsonProperty("error_message")
    private String errorMessage;
}
