package com.osgateway.gatewaydevice.infrastructure.ws;

import com.osgateway.gatewaydevice.api.dto.GatewayDtos.GatewayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GatewayWsPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public void publish(GatewayResponse gateway) {
        messagingTemplate.convertAndSend("/topic/gateways", gateway);
    }
}