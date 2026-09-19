package com.osgateway.gatewaydevice.infrastructure.persistence;

import com.osgateway.common.enums.GatewayStatus;
import com.osgateway.gatewaydevice.domain.GatewayDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GatewayRepository extends JpaRepository<GatewayDevice, Long> {
    Optional<GatewayDevice> findByDeviceId(String deviceId);
    boolean existsByDeviceId(String deviceId);

    @Query("""
        SELECT g FROM GatewayDevice g
        WHERE (:operator IS NULL OR g.operator = :operator)
          AND (:status IS NULL OR g.status = :status)
        ORDER BY g.name
        """)
    List<GatewayDevice> findFiltered(@Param("operator") String operator, @Param("status") GatewayStatus status);

    /**
     * Live terminals only: recent heartbeat (same window as SMS dispatch).
     * Seed/stale ONLINE rows must not steal USSD jobs from the real phone.
     */
    @Query("""
        SELECT g FROM GatewayDevice g
        WHERE g.status = com.osgateway.common.enums.GatewayStatus.ONLINE
          AND g.lastHeartbeatAt >= :minHeartbeat
          AND (:operator IS NULL OR UPPER(g.operator) = UPPER(:operator))
        ORDER BY g.loadScore ASC, g.networkStrength DESC, g.lastHeartbeatAt DESC
        """)
    List<GatewayDevice> findCandidates(
            @Param("operator") String operator,
            @Param("minHeartbeat") Instant minHeartbeat);
}
