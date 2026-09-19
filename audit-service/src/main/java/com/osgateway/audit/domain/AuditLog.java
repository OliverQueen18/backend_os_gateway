package com.osgateway.audit.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_id")
    private Long actorId;
    @Column(name = "actor_name", length = 100)
    private String actorName;
    @Column(nullable = false, length = 100)
    private String action;
    @Column(name = "resource_type", length = 100)
    private String resourceType;
    @Column(name = "resource_id", length = 100)
    private String resourceId;
    @Column(columnDefinition = "TEXT")
    private String details;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(nullable = false)
    private Instant createdAt;
}