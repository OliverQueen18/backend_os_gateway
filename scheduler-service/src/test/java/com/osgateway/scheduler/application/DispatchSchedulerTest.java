package com.osgateway.scheduler.application;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.common.dto.PageResponse;
import com.osgateway.scheduler.infrastructure.client.GatewayClient;
import com.osgateway.scheduler.infrastructure.client.TransactionClient;
import com.osgateway.scheduler.infrastructure.persistence.SchedulerJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchSchedulerTest {
    @Mock TransactionClient transactionClient;
    @Mock GatewayClient gatewayClient;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock SchedulerJobRepository jobRepository;
    @InjectMocks DispatchScheduler dispatchScheduler;

    @Test
    void pollAndDispatch_assignsGateway() {
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionClient.list(eq("QUEUED"), anyInt(), anyInt()))
                .thenReturn(ApiResponse.ok(PageResponse.of(List.of(Map.of(
                        "id", 1L, "operator", "ORANGE", "type", "DEPOT"
                )), 0, 50, 1)));
        when(gatewayClient.select("ORANGE")).thenReturn(ApiResponse.ok(Map.of("id", 9L)));
        dispatchScheduler.pollAndDispatch();
        verify(transactionClient).updateStatus(eq(1L), anyMap());
        verify(rabbitTemplate, atLeastOnce()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}