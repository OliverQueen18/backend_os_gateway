package com.osgateway.ussd.domain;

import com.osgateway.common.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ussd_templates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class UssdTemplate extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;
    /** Code du type d'opération (entité operation_types), ex. DEPOT, ACHAT_UV. */
    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;
    @Column(nullable = false, length = 150)
    private String name;
    private boolean active;
    @Column(columnDefinition = "TEXT")
    private String description;
}
