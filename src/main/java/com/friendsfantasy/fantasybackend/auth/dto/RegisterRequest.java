package com.friendsfantasy.fantasybackend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    private String fullName;

    @NotBlank
    private String username;

    @NotBlank
    private String mobile;

    @NotBlank
    private String password;
      
    @NotBlank
    private String email;

}