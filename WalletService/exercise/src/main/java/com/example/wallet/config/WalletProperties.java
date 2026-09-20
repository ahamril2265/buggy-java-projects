package com.example.wallet.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "wallet")
public record WalletProperties(
        @NotEmpty List<@Pattern(regexp = "[A-Z]{3}") String> supportedCurrencies,
        @NotNull @Valid Fees fees,
        @NotNull @Valid Limits limits) {

    public record Fees(
            @Min(0) @Max(10_000) int transferBasisPoints,
            @Min(0) long minimumMinor) {
    }

    public record Limits(
            @Positive long maxTransactionMinor,
            @Positive long dailyWithdrawalMinor) {
    }
}
