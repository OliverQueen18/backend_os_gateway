package com.osgateway.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_ERROR("OSG-400", "Validation failed", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("OSG-401", "Unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("OSG-403", "Forbidden", HttpStatus.FORBIDDEN),
    NOT_FOUND("OSG-404", "Resource not found", HttpStatus.NOT_FOUND),
    CONFLICT("OSG-409", "Conflict", HttpStatus.CONFLICT),
    BUSINESS_ERROR("OSG-422", "Business rule violation", HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL_ERROR("OSG-500", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),

    INVALID_CREDENTIALS("OSG-AUTH-001", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("OSG-AUTH-002", "Token expired", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID("OSG-AUTH-003", "Invalid token", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID("OSG-AUTH-004", "Invalid refresh token", HttpStatus.UNAUTHORIZED),
    USER_DISABLED("OSG-AUTH-005", "User account is disabled", HttpStatus.FORBIDDEN),

    USER_NOT_FOUND("OSG-USR-001", "User not found", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS("OSG-USR-002", "User already exists", HttpStatus.CONFLICT),
    ROLE_NOT_FOUND("OSG-USR-003", "Role not found", HttpStatus.NOT_FOUND),

    GATEWAY_NOT_FOUND("OSG-GW-001", "Gateway not found", HttpStatus.NOT_FOUND),
    GATEWAY_OFFLINE("OSG-GW-002", "No suitable online gateway", HttpStatus.SERVICE_UNAVAILABLE),
    GATEWAY_ALREADY_REGISTERED("OSG-GW-003", "Gateway already registered", HttpStatus.CONFLICT),

    OPERATOR_NOT_FOUND("OSG-USSD-001", "Operator not found", HttpStatus.NOT_FOUND),
    TEMPLATE_NOT_FOUND("OSG-USSD-002", "USSD template not found", HttpStatus.NOT_FOUND),
    SCENARIO_ERROR("OSG-USSD-003", "USSD scenario execution error", HttpStatus.UNPROCESSABLE_ENTITY),

    TRANSACTION_NOT_FOUND("OSG-TX-001", "Transaction not found", HttpStatus.NOT_FOUND),
    INVALID_TRANSACTION_STATUS("OSG-TX-002", "Invalid transaction status transition", HttpStatus.CONFLICT),
    INSUFFICIENT_BALANCE("OSG-TX-003", "Insufficient balance", HttpStatus.UNPROCESSABLE_ENTITY),

    SMS_SEND_FAILED("OSG-SMS-001", "SMS send failed", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
