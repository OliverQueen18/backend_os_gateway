package com.osgateway.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    @Email
    private String email;
    @NotBlank
    @Size(min = 6, max = 6)
    private String code;
    @NotBlank
    @Size(min = 8, max = 100)
    private String newPassword;
}
