package com.osgateway.gateway.filter;

import com.osgateway.common.security.JwtService;
import com.osgateway.common.security.SecurityConstants;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/v1/auth/",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui",
            "/webjars"
    );

    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublic(path) || "OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith(SecurityConstants.BEARER_PREFIX)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String token = auth.substring(SecurityConstants.BEARER_PREFIX.length());
            if (!jwtService.isAccessToken(token)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            Claims claims = jwtService.parseClaims(token);
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(jwtService.extractUserId(claims)))
                    .header("X-Username", claims.getSubject())
                    .header("X-Roles", String.join(",", jwtService.extractRoles(claims)))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception ex) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isPublic(String path) {
        if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        // Logos opérateurs (affichage mobile / admin sans token sur <img>)
        return path.matches("^/api/v1/ussd/operators/\\d+/logo$");
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
