package com.osgateway.reporting.api;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.reporting.application.ExportFile;
import com.osgateway.reporting.application.ReportFilters;
import com.osgateway.reporting.application.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reporting")
public class ReportingController {
    private final ReportingService reportingService;

    @GetMapping("/transactions")
    public ApiResponse<Map<String, Object>> transactions(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) Long distributorId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long gatewayId) {
        return ApiResponse.ok(reportingService.transactionsReport(
                from, to, new ReportFilters(distributorId, type, gatewayId)));
    }

    @GetMapping("/sms")
    public ApiResponse<Map<String, Object>> sms(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return ApiResponse.ok(reportingService.smsReport(from, to));
    }

    @GetMapping("/commissions")
    public ApiResponse<Map<String, Object>> commissions(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) Long distributorId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long gatewayId) {
        return ApiResponse.ok(reportingService.commissionsReport(
                from, to, new ReportFilters(distributorId, type, gatewayId)));
    }

    @GetMapping("/commissions/me")
    public ApiResponse<Map<String, Object>> commissionsMe(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long gatewayId) {
        return ApiResponse.ok(reportingService.commissionsReportForUser(
                from, to, userId, new ReportFilters(null, type, gatewayId)));
    }

    @GetMapping("/stats/me")
    public ApiResponse<Map<String, Object>> statsMe(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(reportingService.myStats(userId));
    }

    @GetMapping("/operators")
    public ApiResponse<Map<String, Object>> operators() {
        return ApiResponse.ok(reportingService.operatorsReport());
    }

    @GetMapping("/gateways")
    public ApiResponse<Map<String, Object>> gateways() {
        return ApiResponse.ok(reportingService.gatewaysReport());
    }

    @GetMapping("/export/{report}.{format}")
    @Operation(summary = "Export rapport CSV (UTF-8, séparateur ;, en-têtes FR)")
    public ResponseEntity<byte[]> export(
            @PathVariable String report,
            @PathVariable String format,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long distributorId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long gatewayId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate toDate = to != null ? to : LocalDate.now();
        ExportFile file = reportingService.exportReport(
                format,
                report,
                fromDate,
                toDate,
                userId,
                roles,
                new ReportFilters(distributorId, type, gatewayId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file.filename()))
                .header(HttpHeaders.CONTENT_TYPE, file.contentType())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    private static String contentDisposition(String filename) {
        String safe = filename.replace("\"", "");
        return "attachment; filename=\"" + safe + "\"; filename*=UTF-8''" + safe;
    }
}
