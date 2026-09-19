package com.osgateway.monitoring.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    void dashboardStats_aggregates() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("status", "ONLINE", "count", 2)));
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class))).thenReturn(2L, 5L);
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Double.class))).thenReturn(1.5, 0.5);
        MonitoringService service = new MonitoringService(jdbcTemplate, new SimpleMeterRegistry());
        Map<String, Object> stats = service.dashboardStats();
        assertEquals(2L, stats.get("gatewaysOnline"));
    }
}