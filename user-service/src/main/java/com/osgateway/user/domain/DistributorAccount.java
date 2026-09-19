package com.osgateway.user.domain;

import com.osgateway.common.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "distributor_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistributorAccount extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "first_name", length = 100)
    private String firstName;
    @Column(name = "last_name", length = 100)
    private String lastName;
    @Column(length = 255)
    private String email;
    @Column(length = 30)
    private String phone;
    @Column(length = 500)
    private String address;
    private Double latitude;
    private Double longitude;
    @Column(length = 50)
    private String rccm;
    @Column(length = 50)
    private String nif;
    @Column(length = 50)
    private String nina;
    @Column(precision = 18, scale = 2)
    private BigDecimal balance;
    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;
    /** BCrypt hash of the 4–6 digit transaction PIN. */
    @Column(name = "pin_hash", length = 255)
    private String pinHash;
    private boolean active;

    @Column(name = "registration_status", nullable = false, length = 30)
    @Builder.Default
    private String registrationStatus = "APPROVED";

    @Column(name = "registration_fee_amount", precision = 18, scale = 2)
    private BigDecimal registrationFeeAmount;

    @Column(name = "registration_fee_paid", nullable = false)
    @Builder.Default
    private boolean registrationFeePaid = false;

    @Column(name = "registration_fee_paid_at")
    private Instant registrationFeePaidAt;

    @Column(name = "registration_fee_payment_ref", length = 64)
    private String registrationFeePaymentRef;

    @Column(name = "registration_fee_payment_method", length = 30)
    private String registrationFeePaymentMethod;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;
}
