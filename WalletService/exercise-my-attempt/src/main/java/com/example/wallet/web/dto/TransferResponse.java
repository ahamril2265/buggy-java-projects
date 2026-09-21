package com.example.wallet.web.dto;

import com.example.wallet.domain.Transfer;
import com.example.wallet.domain.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID id, UUID sourceWalletId, UUID targetWalletId, long amountMinor, long feeMinor,
                               String currency, TransferStatus status, long refundedMinor, Instant createdAt) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(transfer.getId(), transfer.getSourceWalletId(), transfer.getTargetWalletId(),
                transfer.getAmountMinor(), transfer.getFeeMinor(), transfer.getCurrency(), transfer.getStatus(),
                transfer.getRefundedMinor(), transfer.getCreatedAt());
    }
}
