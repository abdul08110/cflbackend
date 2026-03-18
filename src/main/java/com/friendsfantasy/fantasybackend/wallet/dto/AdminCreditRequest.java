package com.friendsfantasy.fantasybackend.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminCreditRequest {
    @Min(1)
    private Integer points;

    @NotBlank
    private String remarks;
}