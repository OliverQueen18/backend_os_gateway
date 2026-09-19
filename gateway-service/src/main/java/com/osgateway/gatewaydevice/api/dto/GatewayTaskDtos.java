package com.osgateway.gatewaydevice.api.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GatewayTaskDtos {
    private GatewayTaskDtos() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GatewayTask {
        private String id;
        private String type;
        private String operator;
        private String transactionId;
        private String phone;
        private Double amount;
        private String pin;
        @Builder.Default
        private int priority = 5;
        private String ussdCode;
        private String smsBody;
        private String smsTo;
        @Builder.Default
        private List<UssdStepDto> steps = new ArrayList<>();
        @Builder.Default
        private Map<String, String> variables = new LinkedHashMap<>();
        @Builder.Default
        private int timeoutSeconds = 120;
        /** Étapes USSD SOLDE pour lecture solde avant/après transaction. */
        @Builder.Default
        private List<UssdStepDto> balanceCheckSteps = new ArrayList<>();
        /** Motifs regex extraction solde (paramétrables par opérateur). */
        @Builder.Default
        private List<BalancePatternDto> balancePatterns = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalancePatternDto {
        private String fieldType;
        private String regexPattern;
        @Builder.Default
        private int priority = 10;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UssdStepDto {
        private int order;
        private String action;
        private String value;
        private String expectedPattern;
        private Long timeoutMs;
        private String variableName;
        private String clickLabel;
        /** Étapes du modèle appelé (RUN_TEMPLATE). */
        @Builder.Default
        private List<UssdStepDto> nestedSteps = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskResultRequest {
        private String taskId;
        private String status;
        private String ussdResponse;
        private Map<String, String> extracted;
        private Long durationMs;
        private String errorMessage;
        private String screenshotBase64;
    }
}
