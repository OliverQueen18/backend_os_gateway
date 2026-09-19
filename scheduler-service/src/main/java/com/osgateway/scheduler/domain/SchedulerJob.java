package com.osgateway.scheduler.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "scheduler_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SchedulerJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(columnDefinition = "TEXT")
    private String details;
    private Instant startedAt;
    private Instant finishedAt;
}