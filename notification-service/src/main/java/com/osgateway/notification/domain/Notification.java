package com.osgateway.notification.domain;

import com.osgateway.common.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50)
    private String type;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "gateway_id")
    private Long gatewayId;
    @Column(name = "read_flag")
    private boolean readFlag;
    @Column(length = 30)
    private String severity;
}