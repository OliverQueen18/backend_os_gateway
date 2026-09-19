package com.osgateway.common.security;

import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenMinutes;
    private final long refreshTokenDays;

    public JwtService(String secret, long accessTokenMinutes, long refreshTokenDays) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenDays = refreshTokenDays;
    }

    public String generateAccessToken(Long userId, String username, Collection<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenMinutes * 60);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claims(Map.of(
                        SecurityConstants.CLAIM_USER_ID, userId,
                        SecurityConstants.CLAIM_ROLES, List.copyOf(roles),
                        SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_ACCESS
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(refreshTokenDays * 24 * 60 * 60);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claims(Map.of(
                        SecurityConstants.CLAIM_USER_ID, userId,
                        SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_REFRESH
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Token is invalid");
        }
    }

    public boolean isAccessToken(String token) {
        Claims claims = parseClaims(token);
        return SecurityConstants.TOKEN_TYPE_ACCESS.equals(claims.get(SecurityConstants.CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        Claims claims = parseClaims(token);
        return SecurityConstants.TOKEN_TYPE_REFRESH.equals(claims.get(SecurityConstants.CLAIM_TOKEN_TYPE, String.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get(SecurityConstants.CLAIM_ROLES);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public Long extractUserId(Claims claims) {
        Object userId = claims.get(SecurityConstants.CLAIM_USER_ID);
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return userId != null ? Long.valueOf(userId.toString()) : null;
    }

    public Instant getExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }
}
