package com.osgateway.gateway.filter;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Simple Redis-backed rate limiting stub (fixed window per client IP).
 * Auth and health endpoints are excluded so login remains available under load.
 */
@Component
@RequiredArgsConstructor
public class RedisRateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${osgateway.rate-limit.requests-per-minute:300}")
    private long requestsPerMinute;

    @Value("${osgateway.rate-limit.enabled:true}")
    private boolean enabled;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled || isExcluded(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange);
        }

        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
        } else {
            ip = ip.split(",")[0].trim();
        }
        String key = "osg:ratelimit:" + ip;
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    Mono<Boolean> expire = count != null && count == 1
                            ? redisTemplate.expire(key, Duration.ofMinutes(1))
                            : Mono.just(true);
                    return expire.then(Mono.defer(() -> {
                        if (count != null && count > requestsPerMinute) {
                            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            return exchange.getResponse().setComplete();
                        }
                        return chain.filter(exchange);
                    }));
                })
                .onErrorResume(ex -> chain.filter(exchange));
    }

    private boolean isExcluded(String path) {
        if (path == null) return true;
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator/")
                || path.equals("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
