package com.osgateway.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    /** Identifiant : username ou numéro de téléphone (national ou international). */
    @NotBlank
    private String username;
    @NotBlank
    private String password;
}
