package com.example.wallet.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private Wallet wallet() {
        return Wallet.open("owner-1", "USD", NOW);
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(expected);
    }

    @Test
    void newWalletIsActiveAndEmpty() {
        Wallet wallet = wallet();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(wallet.getBalanceMinor()).isZero();
        assertThat(wallet.getId()).isNotNull();
    }

    @Test
    void creditAndDebitAdjustTheBalance() {
        Wallet wallet = wallet();
        wallet.credit(1_000, NOW);
        wallet.debit(250, NOW);
        assertThat(wallet.getBalanceMinor()).isEqualTo(750);
    }

    @Test
    void canDebitTheEntireBalance() {
        Wallet wallet = wallet();
        wallet.credit(500, NOW);
        wallet.debit(500, NOW);
        assertThat(wallet.getBalanceMinor()).isZero();
    }

    @Test
    void debitBeyondTheBalanceIsRejectedAndLeavesTheBalanceUntouched() {
        Wallet wallet = wallet();
        wallet.credit(500, NOW);
        assertCode(() -> wallet.debit(501, NOW), ErrorCode.INSUFFICIENT_FUNDS);
        assertThat(wallet.getBalanceMinor()).isEqualTo(500);
    }

    @Test
    void creditingPastTheMaximumBalanceIsRejectedInsteadOfWrappingAround() {
        Wallet wallet = wallet();
        wallet.credit(Long.MAX_VALUE, NOW);
        assertCode(() -> wallet.credit(1, NOW), ErrorCode.BALANCE_OVERFLOW);
        assertThat(wallet.getBalanceMinor()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        Wallet wallet = wallet();
        assertThatThrownBy(() -> wallet.credit(0, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.credit(-1, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.debit(-1, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void frozenWalletRejectsCreditsAndDebits() {
        Wallet wallet = wallet();
        wallet.credit(100, NOW);
        wallet.changeStatus(WalletStatus.FROZEN, NOW);
        assertCode(() -> wallet.credit(1, NOW), ErrorCode.WALLET_NOT_ACTIVE);
        assertCode(() -> wallet.debit(1, NOW), ErrorCode.WALLET_NOT_ACTIVE);
    }

    @Test
    void frozenWalletCanBeReactivated() {
        Wallet wallet = wallet();
        wallet.changeStatus(WalletStatus.FROZEN, NOW);
        wallet.changeStatus(WalletStatus.ACTIVE, NOW);
        wallet.credit(100, NOW);
        assertThat(wallet.getBalanceMinor()).isEqualTo(100);
    }

    @Test
    void closedIsTerminal() {
        Wallet wallet = wallet();
        wallet.changeStatus(WalletStatus.CLOSED, NOW);
        assertCode(() -> wallet.changeStatus(WalletStatus.ACTIVE, NOW), ErrorCode.INVALID_STATUS_TRANSITION);
        assertCode(() -> wallet.changeStatus(WalletStatus.FROZEN, NOW), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void onlyAnEmptyWalletCanBeClosed() {
        Wallet wallet = wallet();
        wallet.credit(1, NOW);
        assertCode(() -> wallet.changeStatus(WalletStatus.CLOSED, NOW), ErrorCode.WALLET_NOT_EMPTY);
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    void frozenWalletWithFundsCannotBeClosedEither() {
        Wallet wallet = wallet();
        wallet.credit(1, NOW);
        wallet.changeStatus(WalletStatus.FROZEN, NOW);
        assertCode(() -> wallet.changeStatus(WalletStatus.CLOSED, NOW), ErrorCode.WALLET_NOT_EMPTY);
    }

    @Test
    void settingTheCurrentStatusIsANoOp() {
        Wallet wallet = wallet();
        wallet.changeStatus(WalletStatus.ACTIVE, NOW);
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    void systemWalletsAreRecognisedByOwner() {
        assertThat(Wallet.open(Wallet.SYSTEM_OWNER, "USD", NOW).isSystemWallet()).isTrue();
        assertThat(wallet().isSystemWallet()).isFalse();
    }
}
