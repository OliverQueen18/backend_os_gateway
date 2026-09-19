package com.osgateway.auth.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ForgotPasswordResponse {
    private String message;
    /** Present only when osgateway.auth.expose-reset-code=true (dev). */
    private String resetCode;
}
