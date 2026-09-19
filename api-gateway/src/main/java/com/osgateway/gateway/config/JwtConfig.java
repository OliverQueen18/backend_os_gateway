package com.osgateway.gateway.config;

import com.osgateway.common.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtService jwtService(@Value("${osgateway.jwt.secret}") String secret) {
        return new JwtService(secret, 30, 7);
    }
}
