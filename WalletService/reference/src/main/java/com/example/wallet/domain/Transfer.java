package com.example.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    @Column(name = "source_wallet_id", nullable = false, updatable = false)
    private UUID sourceWalletId;

    @Column(name = "target_wallet_id", nullable = false, updatable = false)
    private UUID targetWalletId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(name = "fee_minor", nullable = false, updatable = false)
    private long feeMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "refunded_minor", nullable = false)
    private long refundedMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TransferStatus status;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transfer() {
    }

    public static Transfer create(UUID sourceWalletId, UUID targetWalletId, long amountMinor, long feeMinor,
                                  String currency, Instant now) {
        Transfer transfer = new Transfer();
        transfer.id = UUID.randomUUID();
        transfer.sourceWalletId = sourceWalletId;
        transfer.targetWalletId = targetWalletId;
        transfer.amountMinor = amountMinor;
        transfer.feeMinor = feeMinor;
        transfer.currency = currency;
        transfer.refundedMinor = 0;
        transfer.status = TransferStatus.COMPLETED;
        transfer.createdAt = now;
        return transfer;
    }

    public long refundableMinor() {
        return amountMinor - refundedMinor;
    }

    public void applyRefund(long refundMinor) {
        if (refundMinor <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        if (refundMinor > refundableMinor()) {
            throw new DomainException(ErrorCode.REFUND_EXCEEDS_TRANSFER,
                    "Refund of " + refundMinor + " exceeds the refundable amount of " + refundableMinor());
        }
        refundedMinor += refundMinor;
        status = refundedMinor == amountMinor ? TransferStatus.REFUNDED : TransferStatus.PARTIALLY_REFUNDED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceWalletId() {
        return sourceWalletId;
    }

    public UUID getTargetWalletId() {
        return targetWalletId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public long getFeeMinor() {
        return feeMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public long getRefundedMinor() {
        return refundedMinor;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
