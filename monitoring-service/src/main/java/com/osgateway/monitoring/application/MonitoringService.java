package com.osgateway.monitoring.application;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MonitoringService {
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    public Map<String, Object> dashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        List<Map<String, Object>> gatewayCounts = jdbcTemplate.queryForList(
                "SELECT status, COUNT(*) AS count FROM gateways GROUP BY status");
        stats.put("gatewaysByStatus", gatewayCounts);
        Long online = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gateways WHERE status = 'ONLINE'", Long.class);
        stats.put("gatewaysOnline", online != null ? online : 0);
        Double txPerMin = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)::float / GREATEST(EXTRACT(EPOCH FROM (NOW() - MIN(created_at)))/60.0, 1)
            FROM transactions WHERE created_at > NOW() - INTERVAL '1 hour'
            """, Double.class);
        Double smsPerMin = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)::float / GREATEST(EXTRACT(EPOCH FROM (NOW() - MIN(created_at)))/60.0, 1)
            FROM sms WHERE created_at > NOW() - INTERVAL '1 hour'
            """, Double.class);
        stats.put("txPerMin", txPerMin != null ? txPerMin : 0);
        stats.put("smsPerMin", smsPerMin != null ? smsPerMin : 0);
        Long alerts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE created_at > NOW() - INTERVAL '24 hour'", Long.class);
        stats.put("alerts24h", alerts != null ? alerts : 0);
        meterRegistry.gauge("osgateway.gateways.online", online != null ? online : 0);
        return stats;
    }

    public List<Map<String, Object>> gatewayHealth() {
        return jdbcTemplate.queryForList("""
            SELECT id, device_id, name, operator, status, battery_level, network_strength,
                   internet_available, last_heartbeat_at, load_score, temperature
            FROM gateways
            ORDER BY last_heartbeat_at DESC NULLS LAST
            """);
    }
}