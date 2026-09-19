package com.osgateway.sms.api;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.common.dto.PageResponse;
import com.osgateway.sms.application.SmsService;
import com.osgateway.sms.domain.AutoReplyRule;
import com.osgateway.sms.domain.SmsMessage;
import com.osgateway.sms.infrastructure.persistence.AutoReplyRuleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sms")
@RequiredArgsConstructor
@Tag(name = "SMS")
public class SmsController {

    private final SmsService smsService;
    private final AutoReplyRuleRepository ruleRepository;

    @PostMapping("/send")
    @Operation(summary = "Send a single SMS")
    public ApiResponse<SmsMessage> send(@RequestBody SmsService.SendRequest request) {
        return ApiResponse.ok(smsService.send(request));
    }

    @PostMapping("/report")
    @Operation(summary = "Gateway report: inbound/outbound SMS delivery feedback")
    public ApiResponse<Map<String, String>> report(@RequestBody SmsService.ReportRequest request) {
        return ApiResponse.ok(smsService.reportFromGateway(request));
    }

    @GetMapping("/history")
    @Operation(summary = "Paginated SMS history")
    public ApiResponse<PageResponse<SmsMessage>> history(
            @RequestParam(required = false) String recipient,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(smsService.history(recipient, page, size));
    }

    @PostMapping("/bulk")
    public ApiResponse<List<SmsMessage>> bulk(@RequestBody SmsService.BulkRequest request) {
        return ApiResponse.ok(smsService.bulk(request));
    }

    @PostMapping("/schedule")
    public ApiResponse<SmsMessage> schedule(@RequestBody SmsService.ScheduleRequest request) {
        return ApiResponse.ok(smsService.schedule(request));
    }

    @GetMapping("/auto-reply")
    public ApiResponse<List<AutoReplyRule>> rules() {
        return ApiResponse.ok(ruleRepository.findAll());
    }

    @PostMapping("/auto-reply")
    public ApiResponse<AutoReplyRule> createRule(@RequestBody AutoReplyRule rule) {
        return ApiResponse.ok(ruleRepository.save(rule));
    }

    @PostMapping("/auto-reply/match")
    public ApiResponse<Map<String, String>> match(@RequestBody Map<String, String> body) {
        String reply = smsService.matchAutoReply(body.get("content"));
        return ApiResponse.ok(Map.of("reply", reply != null ? reply : ""));
    }
}