package com.osgateway.common.security;

public final class SecurityConstants {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final long DEFAULT_ACCESS_TOKEN_MINUTES = 30;
    public static final long DEFAULT_REFRESH_TOKEN_DAYS = 7;

    private SecurityConstants() {
    }
}
