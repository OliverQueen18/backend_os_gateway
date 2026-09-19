package com.osgateway.gatewaydevice.domain;

import com.osgateway.common.domain.AuditableEntity;
import com.osgateway.common.enums.GatewayStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "gateways")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GatewayDevice extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String deviceId;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 30)
    private String operator;
    @Column(length = 30)
    private String phoneNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GatewayStatus status;
    private int loadScore;
    private int batteryLevel;
    private int networkStrength;
    private Double latitude;
    private Double longitude;
    private Long memoryFreeMb;
    private Long storageFreeMb;
    private Double temperature;
    private boolean internetAvailable;
    private Instant lastHeartbeatAt;
    private Instant lastIdleAt;
    @Column(length = 255)
    private String apiKeyHash;
    /** PIN USSD / Mobile Money du terminal — utilisé pour {{pin}} dans les templates. */
    @Column(name = "ussd_pin", length = 32)
    private String ussdPin;
}