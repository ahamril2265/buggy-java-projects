package com.example.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWalletRequest(
        @NotBlank @Size(max = 64) String ownerId,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter upper-case ISO currency code") String currency) {
}
