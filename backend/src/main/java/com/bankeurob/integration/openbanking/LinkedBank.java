package com.bankeurob.integration.openbanking;

import com.bankeurob.account.Customer;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "linked_banks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkedBank {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "bank_url", nullable = false)
    private String bankUrl;

    @Column(name = "access_token", nullable = false, length = 1000)
    private String accessToken;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
