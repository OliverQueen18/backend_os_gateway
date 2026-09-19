package com.osgateway.notification.api;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.common.dto.PageResponse;
import com.osgateway.notification.api.dto.DevicePushDtos;
import com.osgateway.notification.application.DevicePushTokenService;
import com.osgateway.notification.application.NotificationService;
import com.osgateway.notification.domain.Notification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final DevicePushTokenService devicePushTokenService;

    @PostMapping("/alerts")
    @Operation(summary = "Create alert (low battery, offline gateway, failed tx)")
    public ApiResponse<Notification> create(@RequestBody NotificationService.AlertRequest request) {
        return ApiResponse.ok(notificationService.createAlert(request));
    }

    @PostMapping("/devices")
    @Operation(summary = "Register FCM device token for the authenticated user")
    public ApiResponse<DevicePushDtos.RegisterResponse> registerDevice(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody DevicePushDtos.RegisterRequest request) {
        return ApiResponse.ok(devicePushTokenService.register(userId, request));
    }

    @DeleteMapping("/devices")
    @Operation(summary = "Unregister FCM device token")
    public ApiResponse<Map<String, String>> unregisterDevice(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam String token) {
        devicePushTokenService.unregister(userId, token);
        return ApiResponse.ok(Map.of("status", "unregistered"));
    }

    @GetMapping
    public ApiResponse<PageResponse<Notification>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.list(userId, page, size));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Acknowledge / mark notification as read")
    public ApiResponse<Notification> acknowledge(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.acknowledge(id));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Acknowledge all notifications")
    public ApiResponse<Integer> acknowledgeAll(@RequestParam(required = false) Long userId) {
        return ApiResponse.ok(notificationService.acknowledgeAll(userId));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE stream for realtime notifications")
    public SseEmitter stream() {
        return notificationService.subscribe();
    }
}
