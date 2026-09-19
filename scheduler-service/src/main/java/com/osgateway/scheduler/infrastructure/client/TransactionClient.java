package com.osgateway.scheduler.infrastructure.client;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.common.dto.PageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "transaction-service", url = "${osgateway.clients.transaction-service-url}")
public interface TransactionClient {
    @GetMapping("/api/v1/transactions")
    ApiResponse<PageResponse<Map<String, Object>>> list(
            @RequestParam("status") String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size);

    @PutMapping("/api/v1/transactions/{id}/status")
    ApiResponse<Map<String, Object>> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);
}
