package com.example.wallet.web.dto;

import com.example.wallet.domain.EntryType;
import com.example.wallet.domain.LedgerEntry;

import java.time.Instant;
import java.util.UUID;

/** {@code amountMinor} is signed: positive credits the wallet, negative debits it. */
public record TransactionResponse(long entryId, UUID transactionId, UUID walletId, EntryType type,
                                  long amountMinor, long balanceAfterMinor, Instant createdAt) {

    public static TransactionResponse from(LedgerEntry entry) {
        return new TransactionResponse(entry.getId(), entry.getTransactionId(), entry.getWalletId(),
                entry.getType(), entry.getAmountMinor(), entry.getBalanceAfterMinor(), entry.getCreatedAt());
    }
}
