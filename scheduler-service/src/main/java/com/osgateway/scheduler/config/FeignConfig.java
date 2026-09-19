package com.osgateway.scheduler.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    /**
     * Service-to-service calls have no JWT. Mark them as back-office so
     * transaction history is not scoped to a distributor user.
     */
    @Bean
    public RequestInterceptor schedulerInternalHeaders() {
        return template -> {
            template.header("X-Roles", "ADMIN");
            template.header("X-Internal-Client", "scheduler-service");
        };
    }
}
