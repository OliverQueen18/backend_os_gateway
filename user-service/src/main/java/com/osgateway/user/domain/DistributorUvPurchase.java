package com.osgateway.user.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "distributor_uv_purchases")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DistributorUvPurchase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "distributor_id", nullable = false)
    private Long distributorId;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;
    @Column(name = "gateway_id")
    private Long gatewayId;
    @Column(length = 64)
    private String reference;
    @Column(columnDefinition = "TEXT")
    private String note;
    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
