package com.osgateway.transaction.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "transaction_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;
    @Column(nullable = false, length = 30)
    private String fromStatus;
    @Column(nullable = false, length = 30)
    private String toStatus;
    @Column(columnDefinition = "TEXT")
    private String note;
    @Column(nullable = false)
    private Instant createdAt;
}