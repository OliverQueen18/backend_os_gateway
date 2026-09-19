package com.osgateway.auth.application;

import com.osgateway.auth.api.dto.*;
import com.osgateway.auth.domain.RefreshToken;
import com.osgateway.auth.domain.UserAccount;
import com.osgateway.auth.infrastructure.persistence.RefreshTokenRepository;
import com.osgateway.auth.infrastructure.persistence.UserAccountRepository;
import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import com.osgateway.common.messaging.QueueConstants;
import com.osgateway.common.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration RESET_CODE_TTL = Duration.ofMinutes(15);
    private static final String RESET_KEY_PREFIX = "pwdreset:";
    private static final String DEFAULT_DISTRIBUTOR_TERMS = """
            Conditions d'utilisation — Distributeur OS Gateway

            1. Le distributeur s'engage à fournir des informations exactes (identité, RCCM, NIF, NINA).
            2. L'inscription n'est active qu'après paiement des frais et validation par l'administrateur.
            3. Le distributeur est responsable de la confidentialité de son PIN et de ses accès.
            4. Toute activité frauduleuse peut entraîner la suspension ou la résiliation du compte.
            5. Les commissions et opérations sont régies par les règles définies par l'opérateur OS Gateway.
            """;

    private final UserAccountRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${osgateway.auth.expose-reset-code:true}")
    private boolean exposeResetCode;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserAccount user = resolveLoginUser(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokens(user);
    }

    /**
     * Résout l'utilisateur par username ou téléphone (ex. +2237…, 2237…, 70 00 00 00).
     */
    private Optional<UserAccount> resolveLoginUser(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        String trimmed = login.trim();
        Optional<UserAccount> byUsername = userRepository.findByUsernameIgnoreCase(trimmed);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        Optional<UserAccount> byPhoneExact = userRepository.findByPhone(trimmed);
        if (byPhoneExact.isPresent()) {
            return byPhoneExact;
        }
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return Optional.empty();
        }
        Optional<UserAccount> byDigits = userRepository.findByPhoneDigits(digits);
        if (byDigits.isPresent()) {
            return byDigits;
        }
        // Numéro national ML courant (8 chiffres) → préfixe 223
        if (digits.length() >= 8 && digits.length() <= 10 && !digits.startsWith("223")) {
            String local = digits.replaceFirst("^0+", "");
            return userRepository.findByPhoneDigits("223" + local);
        }
        return Optional.empty();
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        if (!jwtService.isRefreshToken(request.getRefreshToken())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        RefreshToken stored = refreshTokenRepository.findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        Claims claims = jwtService.parseClaims(request.getRefreshToken());
        UserAccount user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        refreshTokenRepository.revokeByToken(request.getRefreshToken());
        return issueTokens(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.revokeByToken(request.getRefreshToken());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "Email already taken");
        }
        UserAccount user = UserAccount.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .enabled(true)
                .build();
        user.setCreatedBy("register");
        user = userRepository.save(user);

        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = ?", Long.class, "DISTRIBUTEUR");
        if (roleId != null) {
            jdbcTemplate.update("INSERT INTO user_roles(user_id, role_id) VALUES (?, ?)", user.getId(), roleId);
        }

        createDistributorAccount(user, request);
        notifyRegistration(user);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public RegistrationInfoResponse registrationInfo() {
        return RegistrationInfoResponse.builder()
                .registrationFee(registrationFee())
                .termsOfUse(settingValue(
                        "distributor.registration.terms",
                        DEFAULT_DISTRIBUTOR_TERMS))
                .currency(settingValue("org.currency", "XOF"))
                .build();
    }

    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String genericMessage = "If an account exists for this email, a reset code has been issued.";
        ForgotPasswordResponse.ForgotPasswordResponseBuilder builder = ForgotPasswordResponse.builder()
                .message(genericMessage);

        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (!user.isEnabled()) {
                return;
            }
            String code = String.format("%06d", secureRandom.nextInt(1_000_000));
            redisTemplate.opsForValue().set(
                    RESET_KEY_PREFIX + email,
                    code,
                    RESET_CODE_TTL.toMinutes(),
                    TimeUnit.MINUTES);
            if (exposeResetCode) {
                builder.resetCode(code);
            }
        });
        return builder.build();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String key = RESET_KEY_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equals(request.getCode().trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid or expired reset code");
        }
        UserAccount user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        redisTemplate.delete(key);
    }

    private void createDistributorAccount(UserAccount user, RegisterRequest request) {
        String fullName = user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName().trim()
                : user.getUsername();
        String[] parts = fullName.split("\\s+", 2);
        String firstName = parts[0];
        String lastName = parts.length > 1 ? parts[1] : firstName;
        String code = "DIST-" + user.getId();
        BigDecimal fee = registrationFee();
        String phone = request.getPhone() != null ? request.getPhone() : user.getPhone();
        jdbcTemplate.update(
                """
                INSERT INTO distributor_accounts
                    (user_id, code, name, first_name, last_name, email, phone, address,
                     latitude, longitude, rccm, nif, nina, balance, commission_rate, active,
                     registration_status, registration_fee_amount, registration_fee_paid,
                     submitted_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1.50, FALSE,
                        'FEE_PENDING', ?, FALSE, NOW(), 'register')
                """,
                user.getId(),
                code,
                fullName,
                firstName,
                lastName,
                user.getEmail(),
                phone,
                trimToNull(request.getAddress()),
                request.getLatitude(),
                request.getLongitude(),
                trimToNull(request.getRccm()),
                trimToNull(request.getNif()),
                trimToNull(request.getNina()),
                fee);
    }

    private BigDecimal registrationFee() {
        try {
            String value = settingValue("distributor.registration.fee", "25000");
            if (value == null || value.isBlank()) return new BigDecimal("25000");
            return new BigDecimal(value.trim());
        } catch (Exception e) {
            return new BigDecimal("25000");
        }
    }

    private String settingValue(String key, String defaultValue) {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT value FROM settings WHERE key = ?",
                    String.class,
                    key);
            if (value == null || value.isBlank()) return defaultValue;
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void notifyRegistration(UserAccount user) {
        try {
            rabbitTemplate.convertAndSend(
                    QueueConstants.EXCHANGE,
                    QueueConstants.NOTIFICATION_ROUTING_KEY,
                    Map.of(
                            "type", "REGISTRATION_SUBMITTED",
                            "title", "Inscription reçue",
                            "message", "Complétez le paiement des frais d'inscription pour soumettre votre dossier à validation.",
                            "userId", user.getId(),
                            "severity", "INFO"));
            List<Long> adminIds = jdbcTemplate.queryForList(
                    """
                    SELECT u.id FROM users u
                    JOIN user_roles ur ON ur.user_id = u.id
                    JOIN roles r ON r.id = ur.role_id
                    WHERE r.name IN ('ADMIN','SUPERVISOR') AND u.enabled = TRUE
                    """,
                    Long.class);
            for (Long adminId : adminIds) {
                rabbitTemplate.convertAndSend(
                        QueueConstants.EXCHANGE,
                        QueueConstants.NOTIFICATION_ROUTING_KEY,
                        Map.of(
                                "type", "DISTRIBUTOR_REGISTRATION",
                                "title", "Nouvelle inscription distributeur",
                                "message", "Le distributeur " + user.getUsername() + " vient de s'inscrire (paiement en attente).",
                                "userId", adminId,
                                "severity", "WARN"));
            }
        } catch (Exception ignored) {
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private AuthResponse issueTokens(UserAccount user) {
        List<String> roles = userRepository.findRoleNamesByUserId(user.getId());
        List<String> permissions = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT p.code FROM permissions p
                JOIN role_permissions rp ON rp.permission_id = p.id
                JOIN user_roles ur ON ur.role_id = rp.role_id
                WHERE ur.user_id = ?
                ORDER BY p.code
                """,
                String.class, user.getId());
        String access = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refresh = jwtService.generateRefreshToken(user.getId(), user.getUsername());
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .token(refresh)
                .expiresAt(jwtService.getExpiration(refresh))
                .revoked(false)
                .createdAt(Instant.now())
                .build());
        return AuthResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .roles(roles)
                .permissions(permissions)
                .expiresAt(jwtService.getExpiration(access))
                .build();
    }
}
