package com.osgateway.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class UserDtos {
    private UserDtos() {}

    @Data
    public static class UserRequest {
        @NotBlank private String username;
        @NotBlank @Email private String email;
        private String password;
        private String fullName;
        private String phone;
        private Boolean enabled;
        private List<String> roles;
    }

    @Data @Builder
    public static class UserResponse {
        private Long id;
        private String username;
        private String email;
        private String fullName;
        private String phone;
        private boolean enabled;
        private List<String> roles;
    }

    @Data
    public static class RoleRequest {
        @NotBlank private String name;
        private String description;
        private List<String> permissions;
    }

    @Data @Builder
    public static class RoleResponse {
        private Long id;
        private String name;
        private String description;
        private List<String> permissions;
    }

    @Data
    public static class PermissionRequest {
        @NotBlank private String code;
        private String description;
    }

    @Data @Builder
    public static class PermissionResponse {
        private Long id;
        private String code;
        private String description;
    }

    @Data
    public static class SettingRequest {
        @NotBlank private String key;
        @NotBlank private String value;
        private String description;
    }

    @Data @Builder
    public static class SettingResponse {
        private Long id;
        private String key;
        private String value;
        private String description;
    }

    @Data
    public static class SettingsBulkRequest {
        private List<SettingRequest> settings;
    }

    @Data
    public static class DistributorRequest {
        private Long userId;
        @NotBlank private String code;
        /** Login du compte utilisateur (si vide à la création → dérivé du code). */
        private String username;
        private String name;
        @NotBlank private String firstName;
        @NotBlank private String lastName;
        @NotBlank @Email private String email;
        private String phone;
        private String address;
        private Double latitude;
        private Double longitude;
        private String rccm;
        private String nif;
        /** N° biométrique ou NINA */
        private String nina;
        private BigDecimal balance;
        private BigDecimal commissionRate;
        private Boolean active;
        /** PIN transaction 4–6 chiffres (requis à la création, optionnel à la mise à jour). */
        private String pin;
        /** Mot de passe du compte utilisateur créé (défaut ChangeMe@123). */
        private String password;
    }

    @Data @Builder
    public static class DistributorResponse {
        private Long id;
        private Long userId;
        private String code;
        private String name;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String address;
        private Double latitude;
        private Double longitude;
        private String rccm;
        private String nif;
        private String nina;
        private BigDecimal balance;
        private BigDecimal commissionRate;
        private boolean active;
        private Instant createdAt;
        private String username;
        private boolean hasPin;
        /** Renvoyé uniquement à la création du compte utilisateur lié. */
        private String temporaryPassword;
        private String registrationStatus;
        private BigDecimal registrationFeeAmount;
        private boolean registrationFeePaid;
        private Instant registrationFeePaidAt;
        private String registrationFeePaymentRef;
        private String registrationFeePaymentMethod;
        private String rejectionReason;
        private Instant reviewedAt;
        private Instant submittedAt;
        private int attachmentCount;
    }

    @Data
    public static class RegistrationKycRequest {
        private String rccm;
        private String nif;
        private String nina;
        private String address;
        private Double latitude;
        private Double longitude;
        private String phone;
        private String name;
    }

    @Data
    public static class RegistrationFeePaymentRequest {
        @NotNull @Positive private BigDecimal amount;
        /** CASH | BANK_TRANSFER | MOBILE_MONEY | OTHER */
        @NotBlank private String paymentMethod;
        private String reference;
        private String note;
    }

    @Data
    public static class RejectDistributorRequest {
        @NotBlank private String reason;
    }

    @Data @Builder
    public static class AttachmentResponse {
        private Long id;
        private Long distributorId;
        private String docType;
        private String fileName;
        private String contentType;
        private Long sizeBytes;
        private Instant createdAt;
    }

    /** Changement de PIN par le distributeur authentifié. */
    @Data
    public static class ChangePinRequest {
        @NotBlank private String currentPin;
        @NotBlank private String newPin;
    }

    /** Changement de mot de passe par l'utilisateur authentifié. */
    @Data
    public static class ChangePasswordRequest {
        @NotBlank private String currentPassword;
        @NotBlank private String newPassword;
    }

    @Data
    public static class UvPurchaseRequest {
        @NotNull @Positive private BigDecimal amount;
        /** CASH | GATEWAY_DEPOSIT */
        @NotBlank private String paymentMethod;
        private Long gatewayId;
        private String note;
    }

    @Data @Builder
    public static class UvPurchaseResponse {
        private Long id;
        private Long distributorId;
        private BigDecimal amount;
        private String paymentMethod;
        private Long gatewayId;
        private String reference;
        private String note;
        private BigDecimal balanceAfter;
        private Instant createdAt;
    }

    @Data
    public static class CommissionPayoutRequest {
        @NotNull @Positive private BigDecimal amount;
        /** CASH | BANK_TRANSFER | MOBILE_MONEY | OTHER */
        @NotBlank private String paymentMethod;
        private String reference;
        private String note;
    }

    @Data @Builder
    public static class CommissionPayoutResponse {
        private Long id;
        private Long distributorId;
        private BigDecimal amount;
        private String paymentMethod;
        private String reference;
        private String note;
        private BigDecimal unpaidAfter;
        private Instant paidAt;
        private Instant createdAt;
        private String createdBy;
    }

    @Data @Builder
    public static class CommissionBalanceResponse {
        private Long distributorId;
        private String distributorCode;
        private String distributorName;
        private BigDecimal earned;
        private BigDecimal paid;
        private BigDecimal unpaid;
    }

    @Data
    public static class OperationTypeRequest {
        @NotBlank private String code;
        @NotBlank private String label;
        private String description;
        /** PrimeIcons class, e.g. pi pi-arrow-down */
        private String icon;
        /** DEBIT | CREDIT | NONE */
        private String balanceEffect;
        /** PERCENT | FIXED */
        private String commissionMode;
        private BigDecimal commissionValue;
        private BigDecimal adminSharePercent;
        private BigDecimal distributorSharePercent;
        private Boolean active;
        /** When true, PENDING/QUEUED txs of this type can be cancelled from the UI. */
        private Boolean cancellable;
        /** When true, beneficiary phone is required on transaction create. */
        private Boolean requiresPhone;
        /** When true, amount (> 0) is required on transaction create. */
        private Boolean requiresAmount;
    }

    @Data @Builder
    public static class OperationTypeResponse {
        private Long id;
        private String code;
        private String label;
        private String description;
        private String icon;
        private String balanceEffect;
        private String commissionMode;
        private BigDecimal commissionValue;
        private BigDecimal adminSharePercent;
        private BigDecimal distributorSharePercent;
        private boolean active;
        private boolean cancellable;
        private boolean requiresPhone;
        private boolean requiresAmount;
        private List<OperatorCommissionResponse> operatorCommissions;
        private List<CommissionRuleResponse> commissionRules;
    }

    @Data
    public static class OperatorCommissionRequest {
        @NotBlank private String operatorCode;
        /** PERCENT | FIXED */
        private String commissionMode;
        private BigDecimal commissionValue;
        private BigDecimal adminSharePercent;
        private BigDecimal distributorSharePercent;
    }

    @Data @Builder
    public static class OperatorCommissionResponse {
        private String operatorCode;
        private String operatorName;
        private String commissionMode;
        private BigDecimal commissionValue;
        private BigDecimal adminSharePercent;
        private BigDecimal distributorSharePercent;
    }

    @Data
    public static class CommissionRuleRequest {
        @NotBlank private String operatorCode;
        private BigDecimal amountMin;
        private BigDecimal amountMax;
        /** BASE_THEN_SPLIT | DIRECT_ON_AMOUNT */
        @NotBlank private String calculationMode;
        private BigDecimal ratePercent;
        private BigDecimal commissionMin;
        private BigDecimal commissionMax;
        @NotNull private BigDecimal distributorRate;
        @NotNull private BigDecimal adminRate;
        private BigDecimal operatorRate;
        private java.time.LocalDate validFrom;
        private java.time.LocalDate validTo;
        private Boolean active;
        private Integer priority;
    }

    @Data @Builder
    public static class CommissionRuleResponse {
        private Long id;
        private String operatorCode;
        private String operatorName;
        private BigDecimal amountMin;
        private BigDecimal amountMax;
        private String calculationMode;
        private BigDecimal ratePercent;
        private BigDecimal commissionMin;
        private BigDecimal commissionMax;
        private BigDecimal distributorRate;
        private BigDecimal adminRate;
        private BigDecimal operatorRate;
        private java.time.LocalDate validFrom;
        private java.time.LocalDate validTo;
        private boolean active;
        private int priority;
    }
}
