package com.example.wallet.web.dto;

import com.example.wallet.domain.Wallet;
import com.example.wallet.domain.WalletStatus;

import java.time.Instant;
import java.util.UUID;

public record WalletResponse(UUID id, String ownerId, String currency, long balanceMinor, WalletStatus status,
                             Instant createdAt, Instant updatedAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getOwnerId(), wallet.getCurrency(),
                wallet.getBalanceMinor(), wallet.getStatus(), wallet.getCreatedAt(), wallet.getUpdatedAt());
    }
}
