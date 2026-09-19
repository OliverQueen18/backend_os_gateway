package com.osgateway.common.security;

public enum Roles {
    ADMIN,
    SUPERVISOR,
    DISTRIBUTEUR,
    OPERATOR,
    GATEWAY;

    public String authority() {
        return "ROLE_" + name();
    }
}
