package com.example.wallet.web.dto;

import com.example.wallet.domain.WalletStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull WalletStatus status) {
}
