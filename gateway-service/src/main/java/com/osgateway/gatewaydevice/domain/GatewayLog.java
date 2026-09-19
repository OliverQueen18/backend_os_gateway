package com.osgateway.gatewaydevice.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "gateway_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GatewayLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "gateway_id", nullable = false)
    private Long gatewayId;
    @Column(nullable = false, length = 50)
    private String eventType;
    @Column(columnDefinition = "TEXT")
    private String message;
    @Column(nullable = false)
    private Instant createdAt;
}