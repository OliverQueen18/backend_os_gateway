package com.osgateway.gatewaydevice.api.dto;

import com.osgateway.common.enums.GatewayStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.Instant;

public final class GatewayDtos {
    private GatewayDtos() {}

    @Data
    public static class RegisterRequest {
        @NotBlank private String deviceId;
        @NotBlank private String name;
        /** Optionnel si le gateway existe déjà (réutilisation). Obligatoire à la 1re inscription. */
        private String operator;
        private String phoneNumber;
        private String apiKey;
        /** PIN USSD / Mobile Money (4–6 chiffres). */
        private String ussdPin;
    }

    @Data
    public static class UpdateRequest {
        private String name;
        private String phoneNumber;
        private GatewayStatus status;
        /**
         * Nouveau PIN USSD. Null/blank = ne pas modifier.
         * Envoyer une chaîne de 4–6 chiffres pour définir/mettre à jour.
         */
        private String ussdPin;
    }

    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class HeartbeatRequest {
        private int battery;
        private int network;
        private Double latitude;
        private Double longitude;
        private Long memory;
        private Long storage;
        private Double temp;
        private Boolean internet;
        private String networkType;
        private String imei;
        private String androidVersion;
        private String appVersion;
        private String simOperator;
    }

    @Data @Builder
    public static class GatewayResponse {
        private Long id;
        private String deviceId;
        private String name;
        private String operator;
        private String phoneNumber;
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
        /** True si un PIN USSD est configuré (valeur jamais exposée). */
        private boolean ussdPinSet;
        @Builder.Default
        private int pendingTasks = 0;
        @Builder.Default
        private int nextPollSeconds = 30;
    }
}