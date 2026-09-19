package com.osgateway.ussd.api;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.ussd.application.ScenarioEngine;
import com.osgateway.ussd.application.UssdService;
import com.osgateway.ussd.domain.Operator;
import com.osgateway.ussd.domain.UssdStep;
import com.osgateway.ussd.domain.UssdTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ussd")
@RequiredArgsConstructor
@Tag(name = "USSD")
public class UssdController {

    private final UssdService ussdService;

    @GetMapping("/operators")
    @Operation(summary = "List operators (optionally active only)")
    public ApiResponse<List<Operator>> operators(
            @RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(ussdService.listOperators(active));
    }

    @PostMapping("/operators")
    public ApiResponse<Operator> createOperator(@RequestBody Operator operator) {
        return ApiResponse.ok(ussdService.createOperator(operator));
    }

    @PutMapping("/operators/{id}")
    public ApiResponse<Operator> updateOperator(@PathVariable Long id, @RequestBody Operator operator) {
        return ApiResponse.ok(ussdService.updateOperator(id, operator));
    }

    @PostMapping(value = "/operators/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload operator logo image")
    public ApiResponse<Operator> uploadLogo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Logo mis à jour", ussdService.uploadLogo(id, file));
    }

    @GetMapping("/operators/{id}/logo")
    @Operation(summary = "Download operator logo image")
    public ResponseEntity<Resource> getLogo(@PathVariable Long id) {
        UssdService.LogoFile logo = ussdService.loadLogo(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .body(logo.resource());
    }

    @DeleteMapping("/operators/{id}")
    @Operation(summary = "Delete operator (and related USSD templates)")
    public ApiResponse<Void> deleteOperator(@PathVariable Long id) {
        ussdService.deleteOperator(id);
        return ApiResponse.ok("Deleted", null);
    }

    @GetMapping("/operators/{id}/balance-patterns")
    @Operation(summary = "List balance extraction regex patterns for an operator")
    public ApiResponse<List<com.osgateway.ussd.domain.OperatorBalancePattern>> balancePatterns(
            @PathVariable Long id) {
        return ApiResponse.ok(ussdService.listBalancePatterns(id));
    }

    @PutMapping("/operators/{id}/balance-patterns")
    @Operation(summary = "Replace balance extraction regex patterns for an operator")
    public ApiResponse<List<com.osgateway.ussd.domain.OperatorBalancePattern>> saveBalancePatterns(
            @PathVariable Long id,
            @RequestBody List<UssdService.BalancePatternInput> patterns) {
        return ApiResponse.ok("Motifs enregistrés", ussdService.saveBalancePatterns(id, patterns));
    }

    @PostMapping("/templates")
    @Operation(summary = "Create USSD template with ordered steps")
    public ApiResponse<UssdService.TemplateView> createTemplate(@RequestBody TemplateRequest request) {
        return ApiResponse.ok(ussdService.createTemplate(request.getTemplate(), request.getSteps()));
    }

    @PutMapping("/templates/{id}")
    @Operation(summary = "Update USSD template and optionally replace steps")
    public ApiResponse<UssdService.TemplateView> updateTemplate(@PathVariable Long id, @RequestBody TemplateRequest request) {
        return ApiResponse.ok(ussdService.updateTemplate(id, request.getTemplate(), request.getSteps()));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        ussdService.deleteTemplate(id);
        return ApiResponse.ok("Deleted", null);
    }

    @GetMapping("/templates/{id}")
    public ApiResponse<UssdService.TemplateView> getTemplate(@PathVariable Long id) {
        return ApiResponse.ok(ussdService.getTemplate(id));
    }

    @GetMapping("/templates")
    @Operation(summary = "List templates, or get by operator+type when both params are set")
    public ApiResponse<?> templates(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long operatorId) {
        if (operator != null && type != null && !type.isBlank()) {
            return ApiResponse.ok(ussdService.getByOperatorAndType(operator, type));
        }
        return ApiResponse.ok(ussdService.listTemplates(operatorId));
    }

    @PostMapping("/templates/{id}/run")
    @Operation(summary = "Run scenario engine with variable substitution")
    public ApiResponse<ScenarioEngine.ScenarioResult> run(
            @PathVariable Long id,
            @RequestBody RunRequest request) {
        return ApiResponse.ok(ussdService.runScenario(id, request.getVariables(), request.getScreens()));
    }

    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class TemplateRequest {
        private UssdTemplate template;
        private List<UssdStep> steps;
    }

    @Data
    public static class RunRequest {
        private Map<String, String> variables;
        private Map<String, String> screens;
    }
}
