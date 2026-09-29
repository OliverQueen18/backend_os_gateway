package com.osgateway.transaction.api;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.common.dto.PageResponse;
import com.osgateway.common.enums.TransactionStatus;
import com.osgateway.transaction.application.TransactionService;
import com.osgateway.transaction.domain.Transaction;
import com.osgateway.transaction.domain.TransactionHistory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Create transaction and publish to RabbitMQ with priority")
    public ApiResponse<Transaction> create(
            @RequestBody TransactionService.CreateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (request.getUserId() == null) {
            request.setUserId(userId);
        }
        return ApiResponse.ok("Created", transactionService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<Transaction> get(@PathVariable Long id) {
        return ApiResponse.ok(transactionService.get(id));
    }

    @GetMapping
    @Operation(summary = "Transaction history with filters (status, operator, type, distributor, from/to dates)")
    public ApiResponse<PageResponse<Transaction>> history(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long distributorId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-Roles", required = false) String rolesHeader,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long effectiveUserId = resolveHistoryUserId(userId, headerUserId, rolesHeader);
        Instant fromInstant = from != null ? from.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        return ApiResponse.ok(transactionService.history(
                status, operator, type, effectiveUserId, distributorId, fromInstant, toInstant, page, size));
    }

    /**
     * Console admin / superviseur : toutes les transactions.
     * App distributeur : uniquement celles du compte (X-User-Id).
     */
    private static Long resolveHistoryUserId(Long queryUserId, Long headerUserId, String rolesHeader) {
        if (queryUserId != null) {
            return queryUserId;
        }
        if (isBackOffice(rolesHeader)) {
            return null;
        }
        return headerUserId;
    }

    private static boolean isBackOffice(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }
        String roles = rolesHeader.toUpperCase();
        return roles.contains("ADMIN")
                || roles.contains("SUPERVISOR")
                || roles.contains("OPERATOR");
    }

    @GetMapping("/{id}/timeline")
    public ApiResponse<List<TransactionHistory>> timeline(@PathVariable Long id) {
        return ApiResponse.ok(transactionService.timeline(id));
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ApiResponse<Transaction> updateStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request) {
        return ApiResponse.ok(transactionService.updateStatus(id, request.getStatus(), request.getNote(),
                request.getGatewayId(), request.getUssdResponse(), request.getScreenshotUrl(), request.getDurationMs()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a cancellable transaction (reason required; zeros commissions, no UV balance change)")
    public ApiResponse<Transaction> cancel(@PathVariable Long id, @RequestBody CancelRequest request) {
        return ApiResponse.ok("Cancelled", transactionService.cancel(
                id, request.getCancellationReasonId(), request.getNote()));
    }

    @Data
    public static class StatusUpdateRequest {
        private TransactionStatus status;
        private String note;
        private Long gatewayId;
        private String ussdResponse;
        private String screenshotUrl;
        private Long durationMs;
    }

    @Data
    public static class CancelRequest {
        private Long cancellationReasonId;
        private String note;
    }
}
