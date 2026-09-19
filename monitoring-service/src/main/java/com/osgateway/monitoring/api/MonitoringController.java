package com.osgateway.monitoring.api;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.monitoring.application.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring")
public class MonitoringController {
    private final MonitoringService monitoringService;

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard stats: gateway counts, tx/min, sms/min, alerts")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(monitoringService.dashboardStats());
    }

    @GetMapping("/gateways/health")
    public ApiResponse<List<Map<String, Object>>> gatewayHealth() {
        return ApiResponse.ok(monitoringService.gatewayHealth());
    }
}