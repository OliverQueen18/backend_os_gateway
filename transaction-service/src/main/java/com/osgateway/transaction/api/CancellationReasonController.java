package com.osgateway.transaction.api;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.transaction.application.CancellationReasonService;
import com.osgateway.transaction.domain.CancellationReason;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions/cancellation-reasons")
@RequiredArgsConstructor
@Tag(name = "Cancellation reasons")
public class CancellationReasonController {

    private final CancellationReasonService cancellationReasonService;

    @GetMapping
    @Operation(summary = "List cancellation reason types")
    public ApiResponse<List<CancellationReason>> list(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(cancellationReasonService.list(activeOnly));
    }

    @GetMapping("/{id}")
    public ApiResponse<CancellationReason> get(@PathVariable Long id) {
        return ApiResponse.ok(cancellationReasonService.get(id));
    }

    @PostMapping
    public ApiResponse<CancellationReason> create(@RequestBody CancellationReasonService.UpsertRequest request) {
        return ApiResponse.ok("Created", cancellationReasonService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CancellationReason> update(
            @PathVariable Long id,
            @RequestBody CancellationReasonService.UpsertRequest request) {
        return ApiResponse.ok(cancellationReasonService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        cancellationReasonService.delete(id);
        return ApiResponse.ok(null);
    }
}
