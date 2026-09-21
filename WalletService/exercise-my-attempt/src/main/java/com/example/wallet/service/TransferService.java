package com.example.wallet.service;

import com.example.wallet.domain.DomainException;
import com.example.wallet.domain.EntryType;
import com.example.wallet.domain.ErrorCode;
import com.example.wallet.domain.Transfer;
import com.example.wallet.domain.Wallet;
import com.example.wallet.domain.WalletStatus;
import com.example.wallet.repository.TransferRepository;
import com.example.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.ThreadLocalRandom;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class TransferService {

    public record RefundResult(Transfer transfer, UUID transactionId, long refundedNowMinor) {
    }

    private final WalletRepository wallets;
    private final TransferRepository transfers;
    private final LedgerRecorder ledger;
    private final FeePolicy fees;
    private final LimitPolicy limits;
    private final Clock clock;
    private final MeterRegistry meters;

    public TransferService(WalletRepository wallets, TransferRepository transfers, LedgerRecorder ledger,
                           FeePolicy fees, LimitPolicy limits, Clock clock, MeterRegistry meters) {
        this.wallets = wallets;
        this.transfers = transfers;
        this.ledger = ledger;
        this.fees = fees;
        this.limits = limits;
        this.clock = clock;
        this.meters = meters;
    }

    /**
     * Moves {@code amountMinor} from source to target. The sender additionally pays the fee, which
     * is credited to the system fee wallet of the currency, so every transfer's ledger entries sum
     * to zero.
     */
    @Transactional
    public Transfer transfer(UUID sourceId, UUID targetId, long amountMinor, String currency) {
        limits.checkTransactionAmount(amountMinor);

        UUID feeWalletId = wallets.findByOwnerIdAndCurrency(Wallet.SYSTEM_OWNER, currency)
        .orElseThrow(() -> new DomainException(ErrorCode.UNSUPPORTED_CURRENCY, "Unsupported currency: " + currency))
        .getId();

        Map<UUID, Wallet> locked = lockAll(sourceId, targetId, feeWalletId);
        Wallet source = requireUserWallet(locked.get(sourceId), currency);
        Wallet target = requireUserWallet(locked.get(targetId), currency);
        Wallet feeWallet = locked.get(feeWalletId);

        long fee = fees.feeFor(amountMinor);
        Instant now = clock.instant();
        UUID transactionId = UUID.randomUUID();
        Transfer transfer = transfers.save(Transfer.create(sourceId, targetId, amountMinor, fee, currency, now));

        source.debit(amountMinor, now);
        ledger.record(source, transactionId, EntryType.TRANSFER_OUT, -amountMinor, transfer.getId(), now);
        if (fee > 0) {
            source.debit(fee, now);
            ledger.record(source, transactionId, EntryType.FEE, -amountMinor, transfer.getId(), now);
        }

        target.credit(amountMinor, now);
        ledger.record(target, transactionId, EntryType.TRANSFER_IN, amountMinor, transfer.getId(), now);

        if (fee > 0) {
            feeWallet.credit(fee, now);
            ledger.record(feeWallet, transactionId, EntryType.FEE_INCOME, fee, transfer.getId(), now);
        }

        meters.counter("wallet.transfers", "outcome", "completed").increment();
        return transfer;
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(UUID transferId) {
        return transfers.findById(transferId).orElseThrow(() -> transferNotFound(transferId));
    }

    /**
     * Returns {@code refundMinor} from the target back to the source. The cumulative refunded
     * amount can never exceed the transfer amount. The fee is not refunded.
     */
    @Transactional
    public RefundResult refund(UUID transferId, long refundMinor) {
        Transfer transfer = transfers.findById(transferId).orElseThrow(() -> transferNotFound(transferId));
        Map<UUID, Wallet> locked = lockAll(transfer.getSourceWalletId(), transfer.getTargetWalletId());
        Wallet source = locked.get(transfer.getSourceWalletId());
        Wallet target = locked.get(transfer.getTargetWalletId());

        transfer.applyRefund(refundMinor);

        Instant now = clock.instant();
        UUID transactionId = UUID.randomUUID();
        target.debit(refundMinor, now);
        ledger.record(target, transactionId, EntryType.REFUND_OUT, -refundMinor, transfer.getId(), now);
        source.credit(refundMinor, now);
        ledger.record(source, transactionId, EntryType.REFUND_IN, refundMinor, transfer.getId(), now);

        meters.counter("wallet.transfers", "outcome", "refunded").increment();
        return new RefundResult(transfer, transactionId, refundMinor);
    }

    private Map<UUID, Wallet> lockAll(UUID... ids) {
        List<UUID> distinct = new ArrayList<>(new LinkedHashSet<>(List.of(ids)));

        List<Wallet> found = wallets.lockAllByIdOrdered(distinct);   // ONE query, locks all rows in id order

        if (found.size() != distinct.size()) {
            Set<UUID> foundIds = found.stream().map(Wallet::getId).collect(Collectors.toSet());
            UUID missing = distinct.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
            throw new DomainException(ErrorCode.WALLET_NOT_FOUND, "Wallet not found: " + missing);
        }

        for (Wallet wallet : found) {
            if (wallet.getStatus() != WalletStatus.ACTIVE) {
                throw new DomainException(ErrorCode.WALLET_NOT_ACTIVE,
                    "Wallet not active: " + wallet.getId() + " (status=" + wallet.getStatus() + ")");
            }
        }

        return found.stream().collect(Collectors.toMap(Wallet::getId, w -> w));
    }

    private static Wallet requireUserWallet(Wallet wallet, String currency) {
        if (wallet.isSystemWallet()) {
            throw new DomainException(ErrorCode.SYSTEM_WALLET_NOT_ALLOWED, "System wallets cannot be used directly");
        }
        if (!wallet.getCurrency().equals(currency)) {
            throw new DomainException(ErrorCode.CURRENCY_MISMATCH,
                    "Wallet " + wallet.getId() + " holds " + wallet.getCurrency() + ", not " + currency);
        }
        return wallet;
    }

    private static DomainException transferNotFound(UUID transferId) {
        return new DomainException(ErrorCode.TRANSFER_NOT_FOUND, "Transfer not found: " + transferId);
    }
}
