package com.osgateway.notification.infrastructure.persistence;

import com.osgateway.notification.domain.DevicePushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DevicePushTokenRepository extends JpaRepository<DevicePushToken, Long> {
    Optional<DevicePushToken> findByToken(String token);

    List<DevicePushToken> findByUserId(Long userId);

    void deleteByTokenAndUserId(String token, Long userId);
}
