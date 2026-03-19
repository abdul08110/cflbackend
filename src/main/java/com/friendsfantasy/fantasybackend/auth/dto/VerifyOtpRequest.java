package com.friendsfantasy.fantasybackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyOtpRequest {

     @NotBlank
    private String mobile;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String purpose;

    @NotBlank
    private String otp;
}