package com.osgateway.sms.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "sms_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SmsHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sms_id", nullable = false)
    private Long smsId;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(columnDefinition = "TEXT")
    private String details;
    @Column(nullable = false)
    private Instant createdAt;
}