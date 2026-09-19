package com.osgateway.ussd.domain;

import com.osgateway.common.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "operator_balance_patterns")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OperatorBalancePattern extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    /** PRINCIPAL | BONUS_UV */
    @Column(name = "field_type", nullable = false, length = 30)
    private String fieldType;

    @Column(name = "regex_pattern", nullable = false, length = 500)
    private String regexPattern;

    @Column(nullable = false)
    @Builder.Default
    private int priority = 10;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 255)
    private String description;
}
