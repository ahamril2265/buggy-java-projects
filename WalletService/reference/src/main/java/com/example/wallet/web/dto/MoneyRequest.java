package com.example.wallet.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record MoneyRequest(
        @Positive long amountMinor,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter upper-case ISO currency code") String currency) {
}
