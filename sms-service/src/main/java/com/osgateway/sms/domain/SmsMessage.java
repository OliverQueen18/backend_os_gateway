package com.osgateway.sms.domain;

import com.osgateway.common.domain.AuditableEntity;
import com.osgateway.common.enums.Priority;
import com.osgateway.common.enums.SmsStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "sms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SmsMessage extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String recipient;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SmsStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;
    @Column(name = "gateway_id")
    private Long gatewayId;
    private Instant scheduledAt;
    private Instant sentAt;
}