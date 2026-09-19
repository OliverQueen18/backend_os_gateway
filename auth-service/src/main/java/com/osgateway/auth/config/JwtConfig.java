package com.osgateway.auth.config;

import com.osgateway.common.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtService jwtService(
            @Value("${osgateway.jwt.secret}") String secret,
            @Value("${osgateway.jwt.access-token-minutes}") long accessMinutes,
            @Value("${osgateway.jwt.refresh-token-days}") long refreshDays) {
        return new JwtService(secret, accessMinutes, refreshDays);
    }
}