package com.osgateway.user.domain;

import com.osgateway.common.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "operation_types")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OperationType extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @Column(nullable = false, length = 100)
    private String label;
    @Column(columnDefinition = "TEXT")
    private String description;
    /** PrimeIcons class, e.g. pi pi-arrow-down */
    @Column(nullable = false, length = 80)
    @Builder.Default
    private String icon = "pi pi-bolt";
    @Column(name = "balance_effect", nullable = false, length = 20)
    private String balanceEffect;
    /** PERCENT | FIXED */
    @Column(name = "commission_mode", nullable = false, length = 20)
    @Builder.Default
    private String commissionMode = "PERCENT";
    @Column(name = "commission_value", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal commissionValue = new BigDecimal("1.50");
    @Column(name = "admin_share_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal adminSharePercent = new BigDecimal("40.00");
    @Column(name = "distributor_share_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal distributorSharePercent = new BigDecimal("60.00");
    /** When true, PENDING/QUEUED txs of this type can be cancelled from the UI. */
    @Column(nullable = false)
    @Builder.Default
    private boolean cancellable = true;
    /** When true, beneficiary phone is required on transaction create. */
    @Column(name = "requires_phone", nullable = false)
    @Builder.Default
    private boolean requiresPhone = true;
    /** When true, amount (> 0) is required on transaction create. */
    @Column(name = "requires_amount", nullable = false)
    @Builder.Default
    private boolean requiresAmount = true;
    private boolean active;
}
