package com.osgateway.gatewaydevice.application;

import com.osgateway.common.enums.GatewayStatus;
import com.osgateway.common.exception.BusinessException;
import com.osgateway.gatewaydevice.domain.GatewayDevice;
import com.osgateway.gatewaydevice.infrastructure.persistence.GatewayRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewaySelectionServiceTest {
    @Mock GatewayRepository gatewayRepository;
    @InjectMocks GatewaySelectionService selectionService;

    @Test
    void selectBest_picksLowestLoadThenBestNetwork() {
        GatewayDevice a = GatewayDevice.builder().id(1L).operator("ORANGE").status(GatewayStatus.ONLINE)
                .loadScore(2).batteryLevel(50).networkStrength(60).internetAvailable(true)
                .lastHeartbeatAt(Instant.parse("2026-01-01T01:00:00Z")).build();
        GatewayDevice b = GatewayDevice.builder().id(2L).operator("ORANGE").status(GatewayStatus.ONLINE)
                .loadScore(1).batteryLevel(80).networkStrength(90).internetAvailable(true)
                .lastHeartbeatAt(Instant.parse("2026-01-01T01:00:30Z")).build();
        when(gatewayRepository.findCandidates(eq("ORANGE"), any(Instant.class))).thenReturn(List.of(a, b));
        assertEquals(2L, selectionService.selectBest("ORANGE").getId());
    }

    @Test
    void selectBest_empty_throws() {
        when(gatewayRepository.findCandidates(eq("MOOV"), any(Instant.class))).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> selectionService.selectBest("MOOV"));
    }
}
