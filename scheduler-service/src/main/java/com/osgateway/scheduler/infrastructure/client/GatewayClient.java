package com.osgateway.scheduler.infrastructure.client;

import com.osgateway.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "gateway-service", url = "${osgateway.clients.gateway-service-url}")
public interface GatewayClient {
    @GetMapping("/api/v1/gateways/select")
    ApiResponse<Map<String, Object>> select(@RequestParam("operator") String operator);
}
