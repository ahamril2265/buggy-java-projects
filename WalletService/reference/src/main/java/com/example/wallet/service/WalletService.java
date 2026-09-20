package com.example.wallet.service;

import com.example.wallet.config.WalletProperties;
import com.example.wallet.domain.DomainException;
import com.example.wallet.domain.EntryType;
import com.example.wallet.domain.ErrorCode;
import com.example.wallet.domain.LedgerEntry;
import com.example.wallet.domain.Wallet;
import com.example.wallet.domain.WalletStatus;
import com.example.wallet.repository.LedgerEntryRepository;
import com.example.wallet.repository.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final LedgerEntryRepository entries;
    private final LedgerRecorder ledger;
    private final LimitPolicy limits;
    private final WalletProperties properties;
    private final Clock clock;

    public WalletService(WalletRepository wallets, LedgerEntryRepository entries, LedgerRecorder ledger,
                         LimitPolicy limits, WalletProperties properties, Clock clock) {
        this.wallets = wallets;
        this.entries = entries;
        this.ledger = ledger;
        this.limits = limits;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Wallet createWallet(String ownerId, String currency) {
        if (Wallet.SYSTEM_OWNER.equals(ownerId)) {
            throw new DomainException(ErrorCode.SYSTEM_WALLET_NOT_ALLOWED, "Owner id is reserved");
        }
        requireSupportedCurrency(currency);
        if (wallets.findByOwnerIdAndCurrency(ownerId, currency).isPresent()) {
            throw new DomainException(ErrorCode.WALLET_ALREADY_EXISTS,
                    "Owner already has a " + currency + " wallet");
        }
        return wallets.saveAndFlush(Wallet.open(ownerId, currency, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID walletId) {
        return wallets.findById(walletId).orElseThrow(() -> walletNotFound(walletId));
    }

    @Transactional
    public LedgerEntry deposit(UUID walletId, long amountMinor, String currency) {
        Wallet wallet = lockUserWallet(walletId);
        requireCurrency(wallet, currency);
        limits.checkTransactionAmount(amountMinor);

        wallet.credit(amountMinor, clock.instant());
        return ledger.record(wallet, UUID.randomUUID(), EntryType.DEPOSIT, amountMinor, null, clock.instant());
    }

    @Transactional
    public LedgerEntry withdraw(UUID walletId, long amountMinor, String currency) {
        Wallet wallet = lockUserWallet(walletId);
        requireCurrency(wallet, currency);
        limits.checkTransactionAmount(amountMinor);
        limits.checkDailyWithdrawal(walletId, amountMinor);

        wallet.debit(amountMinor, clock.instant());
        return ledger.record(wallet, UUID.randomUUID(), EntryType.WITHDRAWAL, -amountMinor, null, clock.instant());
    }

    @Transactional
    public Wallet changeStatus(UUID walletId, WalletStatus target) {
        Wallet wallet = lockUserWallet(walletId);
        wallet.changeStatus(target, clock.instant());
        return wallet;
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntry> transactions(UUID walletId, int page, int size) {
        if (!wallets.existsById(walletId)) {
            throw walletNotFound(walletId);
        }
        return entries.findByWalletId(walletId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }

    private Wallet lockUserWallet(UUID walletId) {
        List<Wallet> locked = wallets.lockAllByIdOrdered(List.of(walletId));
        if (locked.isEmpty()) {
            throw walletNotFound(walletId);
        }
        Wallet wallet = locked.get(0);
        if (wallet.isSystemWallet()) {
            throw new DomainException(ErrorCode.SYSTEM_WALLET_NOT_ALLOWED, "System wallets cannot be used directly");
        }
        return wallet;
    }

    private void requireSupportedCurrency(String currency) {
        if (!properties.supportedCurrencies().contains(currency)) {
            throw new DomainException(ErrorCode.UNSUPPORTED_CURRENCY, "Unsupported currency: " + currency);
        }
    }

    private static void requireCurrency(Wallet wallet, String currency) {
        if (!wallet.getCurrency().equals(currency)) {
            throw new DomainException(ErrorCode.CURRENCY_MISMATCH,
                    "Wallet " + wallet.getId() + " holds " + wallet.getCurrency() + ", not " + currency);
        }
    }

    private static DomainException walletNotFound(UUID walletId) {
        return new DomainException(ErrorCode.WALLET_NOT_FOUND, "Wallet not found: " + walletId);
    }
}
