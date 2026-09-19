package com.osgateway.sms.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "auto_reply_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AutoReplyRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 255)
    private String matchPattern;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String replyContent;
    private boolean active;
}