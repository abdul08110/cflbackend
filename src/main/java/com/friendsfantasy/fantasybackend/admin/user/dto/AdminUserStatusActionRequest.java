package com.friendsfantasy.fantasybackend.admin.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminUserStatusActionRequest {
    @NotBlank
    private String remarks;
}
