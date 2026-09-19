package com.osgateway.scheduler.api;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.scheduler.application.DispatchScheduler;
import com.osgateway.scheduler.domain.SchedulerJob;
import com.osgateway.scheduler.infrastructure.persistence.SchedulerJobRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
@Tag(name = "Scheduler")
public class SchedulerController {
    private final DispatchScheduler dispatchScheduler;
    private final SchedulerJobRepository jobRepository;

    @PostMapping("/run")
    public ApiResponse<String> runNow() {
        dispatchScheduler.pollAndDispatch();
        return ApiResponse.ok("Triggered", "OK");
    }

    @GetMapping("/jobs")
    public ApiResponse<List<SchedulerJob>> jobs() {
        return ApiResponse.ok(jobRepository.findAll());
    }
}