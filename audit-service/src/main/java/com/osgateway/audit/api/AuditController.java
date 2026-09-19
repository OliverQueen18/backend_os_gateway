package com.osgateway.audit.api;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.common.dto.PageResponse;
import com.osgateway.audit.application.AuditService;
import com.osgateway.audit.domain.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit")
public class AuditController {
    private final AuditService auditService;

    @PostMapping
    @Operation(summary = "Append audit log entry for critical actions")
    public ApiResponse<AuditLog> append(@RequestBody AuditService.AppendRequest request) {
        return ApiResponse.ok(auditService.append(request));
    }

    @GetMapping
    @Operation(summary = "Search audit logs")
    public ApiResponse<PageResponse<AuditLog>> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorName,
            @RequestParam(required = false) String resourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditService.search(action, actorName, resourceType, page, size));
    }
}