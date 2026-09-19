package com.osgateway.auth.application;

import com.osgateway.auth.api.dto.LoginRequest;
import com.osgateway.auth.domain.UserAccount;
import com.osgateway.auth.infrastructure.persistence.RefreshTokenRepository;
import com.osgateway.auth.infrastructure.persistence.UserAccountRepository;
import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserAccountRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RabbitTemplate rabbitTemplate;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService("osgateway-dev-secret-key-change-me-32chars-min", 30, 7);
        authService = new AuthService(
                userRepository, refreshTokenRepository, passwordEncoder, jwtService,
                jdbcTemplate, redisTemplate, rabbitTemplate);
    }

    @Test
    void login_success() {
        UserAccount user = UserAccount.builder()
                .id(1L)
                .username("admin")
                .email("admin@osgateway.com")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .enabled(true)
                .build();
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        when(userRepository.findRoleNamesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Admin@123");

        var response = authService.login(req);
        assertNotNull(response.getAccessToken());
        assertEquals("admin", response.getUsername());
        assertTrue(response.getRoles().contains("ADMIN"));
    }

    @Test
    void login_byPhone_success() {
        UserAccount user = UserAccount.builder()
                .id(2L)
                .username("dist1")
                .email("dist1@osgateway.com")
                .phone("+22370000000")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .enabled(true)
                .build();
        when(userRepository.findByUsernameIgnoreCase("70000000")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("70000000")).thenReturn(Optional.empty());
        when(userRepository.findByPhoneDigits("70000000")).thenReturn(Optional.empty());
        when(userRepository.findByPhoneDigits("22370000000")).thenReturn(Optional.of(user));
        when(userRepository.findRoleNamesByUserId(2L)).thenReturn(List.of("DISTRIBUTEUR"));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoginRequest req = new LoginRequest();
        req.setUsername("70000000");
        req.setPassword("Admin@123");

        var response = authService.login(req);
        assertEquals("dist1", response.getUsername());
        assertTrue(response.getRoles().contains("DISTRIBUTEUR"));
    }

    @Test
    void login_invalidPassword_throws() {
        UserAccount user = UserAccount.builder()
                .id(1L)
                .username("admin")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .enabled(true)
                .build();
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrong");
        assertThrows(BusinessException.class, () -> authService.login(req));
    }
}