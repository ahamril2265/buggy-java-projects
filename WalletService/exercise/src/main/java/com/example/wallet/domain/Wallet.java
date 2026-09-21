package com.example.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    public static final String SYSTEM_OWNER = "SYSTEM_FEES";

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 64, updatable = false)
    private String ownerId;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WalletStatus status;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    public static Wallet open(String ownerId, String currency, Instant now) {
        Wallet wallet = new Wallet();
        wallet.id = UUID.randomUUID();
        wallet.ownerId = ownerId;
        wallet.currency = currency;
        wallet.balanceMinor = 0;
        wallet.status = WalletStatus.ACTIVE;
        wallet.createdAt = now;
        wallet.updatedAt = now;
        return wallet;
    }

    public void credit(long amountMinor, Instant now) {
        requireActive();
        requirePositive(amountMinor);
        if (balanceMinor >= Long.MAX_VALUE) {
            throw new DomainException(ErrorCode.BALANCE_OVERFLOW, "Maximum credit amount reached " + amountMinor);
        }
        balanceMinor = balanceMinor + amountMinor;
        updatedAt = now;
    }

    public void debit(long amountMinor, Instant now) {
        requireActive();
        requirePositive(amountMinor);
        
        if (balanceMinor < amountMinor) {
            throw new DomainException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds in wallet " + id);
        }
        balanceMinor -= amountMinor;
        updatedAt = now;
    }

    public void changeStatus(WalletStatus target, Instant now) {
        if (target == status) {
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new DomainException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot change wallet status from " + status + " to " + target);
        }
        if (target == WalletStatus.CLOSED && balanceMinor != 0) {
            throw new DomainException(ErrorCode.WALLET_NOT_EMPTY, "Only an empty wallet can be closed");
        }
        status = target;
        updatedAt = now;
    }

    public boolean isSystemWallet() {
        return SYSTEM_OWNER.equals(ownerId);
    }

    private void requireActive() {
        if (status != WalletStatus.ACTIVE) {
            throw new DomainException(ErrorCode.WALLET_NOT_ACTIVE, "Wallet " + id + " is " + status);
        }
    }

    private static void requirePositive(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Wallet other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
