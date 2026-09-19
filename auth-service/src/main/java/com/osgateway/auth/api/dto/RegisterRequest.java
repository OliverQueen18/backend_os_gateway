package com.osgateway.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 3, max = 100)
    private String username;
    @NotBlank
    @Email
    private String email;
    @NotBlank
    @Size(min = 8, max = 100)
    private String password;
    private String fullName;
    private String phone;
    private String address;
    private Double latitude;
    private Double longitude;
    private String rccm;
    private String nif;
    /** N° biométrique ou NINA */
    private String nina;
}
