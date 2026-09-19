package com.osgateway.ussd.domain;

import com.osgateway.common.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "operators")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Operator extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    private boolean active;

    /** URL affichable (externe ou /api/v1/ussd/operators/{id}/logo). */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "logo_storage_key", length = 500)
    private String logoStorageKey;

    @Column(name = "logo_content_type", length = 120)
    private String logoContentType;
}
