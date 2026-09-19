package com.osgateway.gatewaydevice.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "gateway_status")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GatewayStatusSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "gateway_id", nullable = false)
    private Long gatewayId;
    private int batteryLevel;
    private int networkStrength;
    private Double latitude;
    private Double longitude;
    private Long memoryFreeMb;
    private Long storageFreeMb;
    private Double temperature;
    private boolean internetAvailable;
    @Column(nullable = false)
    private Instant recordedAt;
}