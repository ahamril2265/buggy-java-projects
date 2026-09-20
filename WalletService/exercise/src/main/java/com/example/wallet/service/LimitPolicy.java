package com.example.wallet.service;

import com.example.wallet.config.WalletProperties;
import com.example.wallet.domain.DomainException;
import com.example.wallet.domain.ErrorCode;
import com.example.wallet.repository.LedgerEntryRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class LimitPolicy {

    private static final Duration DAILY_WINDOW = Duration.ofHours(24);

    private final WalletProperties.Limits limits;
    private final LedgerEntryRepository ledger;
    private final Clock clock;

    public LimitPolicy(WalletProperties properties, LedgerEntryRepository ledger, Clock clock) {
        this.limits = properties.limits();
        this.ledger = ledger;
        this.clock = clock;
    }

    public void checkTransactionAmount(long amountMinor) {
        if (amountMinor > limits.maxTransactionMinor()) {
            throw new DomainException(ErrorCode.AMOUNT_LIMIT_EXCEEDED,
                    "Amount exceeds the per-transaction limit of " + limits.maxTransactionMinor());
        }
    }

    public void checkDailyWithdrawal(UUID walletId, long amountMinor) {
        Instant since = clock.instant().minus(DAILY_WINDOW);
        long alreadyWithdrawn = ledger.sumWithdrawnSince(walletId, since);
        if (alreadyWithdrawn + amountMinor >= limits.dailyWithdrawalMinor()) {
            throw new DomainException(ErrorCode.DAILY_LIMIT_EXCEEDED,
                    "Daily withdrawal limit of " + limits.dailyWithdrawalMinor() + " would be exceeded");
        }
    }
}
