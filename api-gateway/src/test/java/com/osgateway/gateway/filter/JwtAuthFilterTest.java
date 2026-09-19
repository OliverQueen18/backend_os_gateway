package com.osgateway.gateway.filter;

import com.osgateway.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthFilterTest {

    @Test
    void allowsPublicAuthPathWithoutToken() {
        JwtService jwtService = new JwtService("osgateway-dev-secret-key-change-me-32chars-min", 30, 7);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());
        AtomicBoolean called = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            called.set(true);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        assertTrue(called.get());
    }

    @Test
    void allowsValidAccessToken() {
        JwtService jwtService = new JwtService("osgateway-dev-secret-key-change-me-32chars-min", 30, 7);
        String token = jwtService.generateAccessToken(1L, "admin", List.of("ADMIN"));
        JwtAuthFilter filter = new JwtAuthFilter(jwtService);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .build());
        AtomicBoolean called = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            called.set(true);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        assertTrue(called.get());
    }
}
