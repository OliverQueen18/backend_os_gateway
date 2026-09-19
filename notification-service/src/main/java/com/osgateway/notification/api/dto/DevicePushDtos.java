package com.osgateway.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

public final class DevicePushDtos {
    private DevicePushDtos() {}

    @Data
    public static class RegisterRequest {
        @NotBlank
        private String token;
        /** ANDROID | IOS — default ANDROID */
        private String platform;
        /** DISTRIBUTOR | GATEWAY */
        @NotBlank
        private String app;
        private Long gatewayId;
    }

    @Data
    @Builder
    public static class RegisterResponse {
        private Long id;
        private Long userId;
        private String app;
        private String platform;
        private Long gatewayId;
    }
}
