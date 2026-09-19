package com.osgateway.gatewaydevice.api;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.common.enums.GatewayStatus;
import com.osgateway.gatewaydevice.api.dto.GatewayDtos.*;
import com.osgateway.gatewaydevice.api.dto.GatewayTaskDtos;
import com.osgateway.gatewaydevice.application.GatewayService;
import com.osgateway.gatewaydevice.application.GatewayTaskQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/gateways")
@RequiredArgsConstructor
@Tag(name = "Gateways")
public class GatewayController {

    private final GatewayService gatewayService;
    private final GatewayTaskQueueService taskQueueService;

    @GetMapping
    @Operation(summary = "List gateways filtered by operator and status")
    public ApiResponse<List<GatewayResponse>> list(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) GatewayStatus status) {
        return ApiResponse.ok(gatewayService.list(operator, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<GatewayResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(gatewayService.get(id));
    }

    @PostMapping
    @Operation(summary = "Register a new Android gateway")
    public ApiResponse<GatewayResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("Registered", gatewayService.register(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<GatewayResponse> update(@PathVariable Long id, @RequestBody UpdateRequest request) {
        return ApiResponse.ok(gatewayService.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Disable gateway (soft)")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        gatewayService.deactivate(id);
        return ApiResponse.ok("Disabled", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Permanently delete gateway")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        gatewayService.delete(id);
        return ApiResponse.ok("Deleted", null);
    }

    @PostMapping("/{id}/heartbeat")
    @Operation(summary = "Heartbeat every ~30s with battery, network, gps, memory, storage, temp, internet")
    public ApiResponse<GatewayResponse> heartbeat(@PathVariable Long id, @RequestBody HeartbeatRequest request) {
        return ApiResponse.ok(gatewayService.heartbeat(id, request));
    }

    @GetMapping("/{id}/tasks")
    @Operation(summary = "Poll pending USSD/SMS tasks for this gateway")
    public ApiResponse<List<GatewayTaskDtos.GatewayTask>> pollTasks(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") int limit) {
        gatewayService.get(id);
        return ApiResponse.ok(taskQueueService.poll(id, limit));
    }

    @PostMapping("/{id}/tasks/test")
    @Operation(summary = "Enqueue a dummy USSD task (dev/test only)")
    public ApiResponse<GatewayTaskDtos.GatewayTask> enqueueTestTask(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "ORANGE") String operator,
            @RequestParam(required = false, defaultValue = "70000000") String phone) {
        gatewayService.get(id);
        GatewayTaskDtos.GatewayTask task = GatewayTaskDtos.GatewayTask.builder()
                .id("test-" + System.currentTimeMillis())
                .type("USSD")
                .operator(operator)
                .phone(phone)
                .amount(1000.0)
                .ussdCode("*144#")
                .priority(5)
                .timeoutSeconds(60)
                .variables(Map.of("phone", phone, "amount", "1000"))
                .build();
        taskQueueService.enqueue(id, task);
        return ApiResponse.ok("Test task queued", task);
    }

    @PostMapping("/{id}/tasks/test-sms")
    @Operation(summary = "Enqueue a dummy SMS task for this gateway (dev/test only)")
    public ApiResponse<GatewayTaskDtos.GatewayTask> enqueueTestSmsTask(
            @PathVariable Long id,
            @RequestParam String to,
            @RequestParam(required = false, defaultValue = "OS Gateway test SMS") String body) {
        gatewayService.get(id);
        GatewayTaskDtos.GatewayTask task = GatewayTaskDtos.GatewayTask.builder()
                .id("sms-test-" + System.currentTimeMillis())
                .type("SMS")
                .phone(to)
                .smsTo(to)
                .smsBody(body)
                .priority(5)
                .timeoutSeconds(60)
                .build();
        taskQueueService.enqueue(id, task);
        return ApiResponse.ok("Test SMS task queued", task);
    }

    @PostMapping("/tasks/requeue-sending-sms")
    @Operation(summary = "Re-enqueue SMS rows stuck in SENDING (dev/ops)")
    public ApiResponse<Map<String, Object>> requeueSendingSms() {
        int n = taskQueueService.requeueSendingSms();
        return ApiResponse.ok(Map.of("requeued", n));
    }

    @PostMapping("/{id}/tasks/{taskId}/result")
    @Operation(summary = "Report task execution result from Android gateway")
    public ApiResponse<Map<String, String>> reportTaskResult(
            @PathVariable Long id,
            @PathVariable String taskId,
            @RequestBody GatewayTaskDtos.TaskResultRequest request) {
        gatewayService.get(id);
        taskQueueService.acceptResult(id, taskId, request);
        return ApiResponse.ok(Map.of("status", "accepted", "taskId", taskId));
    }

    @GetMapping("/select")
    @Operation(summary = "Select best gateway for operator")
    public ApiResponse<GatewayResponse> select(@RequestParam String operator) {
        return ApiResponse.ok(gatewayService.select(operator));
    }
}
