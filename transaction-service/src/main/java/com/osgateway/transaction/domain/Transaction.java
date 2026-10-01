package com.osgateway.transaction.domain;

import com.osgateway.common.domain.AuditableEntity;
import com.osgateway.common.enums.Priority;
import com.osgateway.common.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String reference;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "distributor_id")
    private Long distributorId;
    @Column(name = "gateway_id")
    private Long gatewayId;
    @Column(nullable = false, length = 30)
    private String operator;
    @Column(nullable = false, length = 30)
    private String type;
    @Column(name = "beneficiary_phone", length = 30)
    private String beneficiaryPhone;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(precision = 18, scale = 2)
    private BigDecimal commission;
    @Column(name = "admin_commission", precision = 18, scale = 2)
    private BigDecimal adminCommission;
    @Column(name = "distributor_commission", precision = 18, scale = 2)
    private BigDecimal distributorCommission;
    @Column(name = "operator_commission", precision = 18, scale = 2)
    private BigDecimal operatorCommission;
    @Column(name = "cancellation_reason_id")
    private Long cancellationReasonId;
    @Column(name = "cancellation_note", columnDefinition = "TEXT")
    private String cancellationNote;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;
    @Column(name = "ussd_response", columnDefinition = "TEXT")
    private String ussdResponse;
    @Column(name = "confirmation_sms", columnDefinition = "TEXT")
    private String confirmationSms;
    @Column(name = "confirmation_received_at")
    private java.time.OffsetDateTime confirmationReceivedAt;
    @Column(name = "orange_transaction_id", length = 80)
    private String orangeTransactionId;
    @Column(name = "confirmation_source", length = 20)
    private String confirmationSource;
    @Column(name = "parsed_amount", precision = 18, scale = 2)
    private BigDecimal parsedAmount;
    @Column(name = "parsed_phone", length = 30)
    private String parsedPhone;
    @Column(name = "gateway_balance_before", precision = 18, scale = 2)
    private BigDecimal gatewayBalanceBefore;
    @Column(name = "gateway_balance_after", precision = 18, scale = 2)
    private BigDecimal gatewayBalanceAfter;
    @Column(name = "gateway_balance_delta", precision = 18, scale = 2)
    private BigDecimal gatewayBalanceDelta;
    @Column(name = "balance_confirmed")
    private Boolean balanceConfirmed;
    @Column(name = "balance_confirmation_source", length = 20)
    private String balanceConfirmationSource;
    @Column(name = "screenshot_url", length = 500)
    private String screenshotUrl;
    private Long durationMs;
}
