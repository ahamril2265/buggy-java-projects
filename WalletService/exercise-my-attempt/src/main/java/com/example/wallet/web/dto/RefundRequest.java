package com.example.wallet.web.dto;

import jakarta.validation.constraints.Positive;

public record RefundRequest(@Positive long amountMinor) {
}
