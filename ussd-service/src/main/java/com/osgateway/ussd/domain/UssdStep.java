package com.osgateway.ussd.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ussd_steps")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class UssdStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "template_id", nullable = false)
    private Long templateId;
    @Column(name = "step_order", nullable = false)
    private int stepOrder;
    @Column(nullable = false, length = 30)
    private String action;
    @Column(columnDefinition = "TEXT")
    private String expression;
    @Column(name = "expected_pattern", length = 255)
    private String expectedPattern;
    @Column(name = "extract_var", length = 100)
    private String extractVar;
    private Integer waitMillis;
}