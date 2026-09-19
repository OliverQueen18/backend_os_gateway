package com.osgateway.auth.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RegistrationInfoResponse {
    private BigDecimal registrationFee;
    private String termsOfUse;
    private String currency;
}
