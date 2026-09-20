package com.example.wallet.service;

import com.example.wallet.domain.EntryType;
import com.example.wallet.domain.LedgerEntry;
import com.example.wallet.domain.Wallet;
import com.example.wallet.repository.LedgerEntryRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class LedgerRecorder {

    private final LedgerEntryRepository entries;

    public LedgerRecorder(LedgerEntryRepository entries) {
        this.entries = entries;
    }

    /**
     * Records a ledger line for a wallet that has <em>already</em> been debited/credited, so the
     * stored {@code balanceAfter} is the wallet's balance at this point in the operation.
     */
    public LedgerEntry record(Wallet wallet, UUID transactionId, EntryType type, long signedAmountMinor,
                              UUID transferId, Instant now) {
        return entries.save(new LedgerEntry(transactionId, wallet.getId(), type, signedAmountMinor,
                wallet.getBalanceMinor(), transferId, now));
    }
}
