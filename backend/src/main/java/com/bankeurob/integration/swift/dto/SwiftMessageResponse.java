package com.bankeurob.integration.swift.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Odpowiedź z SWIFT Middleware API po wysłaniu komunikatu pacs.008.
 * <p>
 * Przykładowa odpowiedź (status 202):
 * <pre>
 * {
 *   "status": "accepted",
 *   "message_id": "MSG-1001",
 *   "uetr": "11111111-1111-4111-8111-111111111111",
 *   "receiver_bank": "Bank UK 1",
 *   "route": ["PLBKPL01XXX", "UKBKGB01XXX"],
 *   "estimated_seconds": 1.0,
 *   "fee_breakdown": { "total_fee": 0.75, ... },
 *   "cancel_window_seconds": 5
 * }
 * </pre>
 */
@Data
public class SwiftMessageResponse {
    private String status;
    @JsonProperty("message_id")
    private String messageId;
    private String uetr;
    @JsonProperty("receiver_bank")
    private String receiverBank;
    private List<String> route;
    @JsonProperty("estimated_seconds")
    private Double estimatedSeconds;
    @JsonProperty("fee_breakdown")
    private Map<String, Object> feeBreakdown;
    @JsonProperty("cancel_window_seconds")
    private Integer cancelWindowSeconds;
    private String error;
}
