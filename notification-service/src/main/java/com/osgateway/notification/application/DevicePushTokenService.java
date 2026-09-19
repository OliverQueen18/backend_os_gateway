package com.osgateway.notification.application;

import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import com.osgateway.notification.api.dto.DevicePushDtos;
import com.osgateway.notification.domain.DevicePushToken;
import com.osgateway.notification.infrastructure.persistence.DevicePushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DevicePushTokenService {

    private static final Set<String> APPS = Set.of("DISTRIBUTOR", "GATEWAY");
    private static final Set<String> PLATFORMS = Set.of("ANDROID", "IOS");

    private final DevicePushTokenRepository repository;

    @Transactional
    public DevicePushDtos.RegisterResponse register(Long userId, DevicePushDtos.RegisterRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "X-User-Id header required");
        }
        String token = request.getToken() == null ? "" : request.getToken().trim();
        if (token.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "token is required");
        }
        String app = normalize(request.getApp(), "DISTRIBUTOR");
        if (!APPS.contains(app)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "app must be DISTRIBUTOR or GATEWAY");
        }
        String platform = normalize(request.getPlatform(), "ANDROID");
        if (!PLATFORMS.contains(platform)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "platform must be ANDROID or IOS");
        }

        DevicePushToken entity = repository.findByToken(token).orElse(null);
        Instant now = Instant.now();
        if (entity == null) {
            entity = DevicePushToken.builder()
                    .userId(userId)
                    .token(token)
                    .platform(platform)
                    .app(app)
                    .gatewayId(request.getGatewayId())
                    .lastSeenAt(now)
                    .createdAt(now)
                    .build();
        } else {
            entity.setUserId(userId);
            entity.setPlatform(platform);
            entity.setApp(app);
            entity.setGatewayId(request.getGatewayId());
            entity.setLastSeenAt(now);
        }
        entity = repository.save(entity);
        return DevicePushDtos.RegisterResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .app(entity.getApp())
                .platform(entity.getPlatform())
                .gatewayId(entity.getGatewayId())
                .build();
    }

    @Transactional
    public void unregister(Long userId, String token) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "X-User-Id header required");
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "token is required");
        }
        repository.deleteByTokenAndUserId(token.trim(), userId);
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
