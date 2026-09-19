package com.osgateway.gatewaydevice.application;

import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import com.osgateway.gatewaydevice.domain.GatewayDevice;
import com.osgateway.gatewaydevice.infrastructure.persistence.GatewayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GatewaySelectionService {

    static final Duration HEARTBEAT_MAX_AGE = Duration.ofMinutes(2);

    private final GatewayRepository gatewayRepository;

    public GatewayDevice selectBest(String operator) {
        String op = operator != null && !operator.isBlank() ? operator.trim() : null;
        Instant minHeartbeat = Instant.now().minus(HEARTBEAT_MAX_AGE);
        List<GatewayDevice> candidates = gatewayRepository.findCandidates(op, minHeartbeat);
        return candidates.stream()
                .min(Comparator
                        .comparingInt(GatewayDevice::getLoadScore)
                        .thenComparing(Comparator.comparingInt(GatewayDevice::getNetworkStrength).reversed())
                        .thenComparing(g -> g.getLastHeartbeatAt() == null ? Instant.EPOCH : g.getLastHeartbeatAt(),
                                Comparator.reverseOrder()))
                .orElseThrow(() -> new BusinessException(ErrorCode.GATEWAY_OFFLINE,
                        "No ONLINE gateway for operator " + operator
                                + " with heartbeat in the last " + HEARTBEAT_MAX_AGE.toMinutes() + " minutes"));
    }
}
