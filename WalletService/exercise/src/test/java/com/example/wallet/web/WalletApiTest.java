package com.example.wallet.web;

import com.example.wallet.domain.Wallet;
import com.example.wallet.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WalletApiTest extends ApiTest {

    // ---- creating and reading wallets ----

    @Test
    void createWalletReturns201WithLocationAndZeroBalance() throws Exception {
        String owner = "owner-" + UUID.randomUUID();
        createWalletRaw(owner, "USD")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/wallets/")))
                .andExpect(jsonPath("$.ownerId").value(owner))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.balanceMinor").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void anOwnerCannotHaveTwoWalletsInTheSameCurrency() throws Exception {
        String owner = "owner-" + UUID.randomUUID();
        createWalletRaw(owner, "USD").andExpect(status().isCreated());
        createWalletRaw(owner, "USD")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_EXISTS"));
    }

    @Test
    void anOwnerMayHaveWalletsInDifferentCurrencies() throws Exception {
        String owner = "owner-" + UUID.randomUUID();
        createWalletRaw(owner, "USD").andExpect(status().isCreated());
        createWalletRaw(owner, "EUR").andExpect(status().isCreated());
    }

    @Test
    void malformedCurrencyIsRejectedWithFieldErrors() throws Exception {
        createWalletRaw("owner-x", "usd")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void blankOwnerIsRejected() throws Exception {
        createWalletRaw("  ", "USD")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ownerId"));
    }

    @Test
    void unsupportedCurrencyIsRejected() throws Exception {
        createWalletRaw("owner-" + UUID.randomUUID(), "JPY")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
    }

    @Test
    void theSystemOwnerIsReserved() throws Exception {
        createWalletRaw(Wallet.SYSTEM_OWNER, "USD")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYSTEM_WALLET_NOT_ALLOWED"));
    }

    @Test
    void unknownWalletReturns404() throws Exception {
        getWallet(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    // ---- deposits ----

    @Test
    void depositIncreasesTheBalanceAndReturnsTheLedgerEntry() throws Exception {
        UUID wallet = createWallet("USD");
        deposit(wallet, 2_500, "USD", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amountMinor").value(2_500))
                .andExpect(jsonPath("$.balanceAfterMinor").value(2_500))
                .andExpect(jsonPath("$.walletId").value(wallet.toString()));
        getWallet(wallet).andExpect(jsonPath("$.balanceMinor").value(2_500));
    }

    @Test
    void depositInTheWrongCurrencyIsRejected() throws Exception {
        UUID wallet = createWallet("USD");
        deposit(wallet, 100, "EUR", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
        assertThat(balanceOf(wallet)).isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -5_000})
    void depositOfANonPositiveAmountIsRejected(long amount) throws Exception {
        UUID wallet = createWallet("USD");
        deposit(wallet, amount, "USD", key())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amountMinor"));
        assertThat(balanceOf(wallet)).isZero();
    }

    @Test
    void depositAboveThePerTransactionLimitIsRejected() throws Exception {
        UUID wallet = createWallet("USD");
        deposit(wallet, 1_000_001, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AMOUNT_LIMIT_EXCEEDED"));
        deposit(wallet, 1_000_000, "USD", key()).andExpect(status().isCreated());
    }

    @Test
    void depositIntoAFrozenWalletIsRejected() throws Exception {
        UUID wallet = createWallet("USD");
        setStatus(wallet, "FROZEN").andExpect(status().isOk());
        deposit(wallet, 100, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));
    }

    @Test
    void depositIntoAClosedWalletIsRejected() throws Exception {
        UUID wallet = createWallet("USD");
        setStatus(wallet, "CLOSED").andExpect(status().isOk());
        deposit(wallet, 100, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));
    }

    @Test
    void depositIntoAnUnknownWalletReturns404() throws Exception {
        deposit(UUID.randomUUID(), 100, "USD", key())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    void systemWalletsCannotBeDepositedIntoDirectly() throws Exception {
        deposit(feeWallet("USD").getId(), 100, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYSTEM_WALLET_NOT_ALLOWED"));
    }

    // ---- withdrawals ----

    @Test
    void withdrawalDecreasesTheBalanceAndRecordsANegativeEntry() throws Exception {
        UUID wallet = fundedWallet("USD", 5_000);
        withdraw(wallet, 1_200, "USD", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.amountMinor").value(-1_200))
                .andExpect(jsonPath("$.balanceAfterMinor").value(3_800));
        assertThat(balanceOf(wallet)).isEqualTo(3_800);
    }

    @Test
    void withdrawalBeyondTheBalanceIsRejectedAndNothingChanges() throws Exception {
        UUID wallet = fundedWallet("USD", 1_000);
        withdraw(wallet, 1_001, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(balanceOf(wallet)).isEqualTo(1_000);
    }

    @Test
    void withdrawalInTheWrongCurrencyIsRejected() throws Exception {
        UUID wallet = fundedWallet("USD", 1_000);
        withdraw(wallet, 100, "EUR", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
        assertThat(balanceOf(wallet)).isEqualTo(1_000);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -500})
    void aNegativeWithdrawalCanNeverBeUsedToAddMoney(long amount) throws Exception {
        UUID wallet = fundedWallet("USD", 1_000);
        withdraw(wallet, amount, "USD", key()).andExpect(status().isBadRequest());
        assertThat(balanceOf(wallet)).isEqualTo(1_000);
    }

    @Test
    void withdrawingTheWholeBalanceIsAllowed() throws Exception {
        UUID wallet = fundedWallet("USD", 1_000);
        withdraw(wallet, 1_000, "USD", key()).andExpect(status().isCreated());
        assertThat(balanceOf(wallet)).isZero();
    }

    @Test
    void withdrawFromAFrozenWalletIsRejected() throws Exception {
        UUID wallet = fundedWallet("USD", 1_000);
        setStatus(wallet, "FROZEN").andExpect(status().isOk());
        withdraw(wallet, 100, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));
    }

    // ---- daily withdrawal limit (500_000 in a rolling 24h window) ----

    @Test
    void theDailyLimitCanBeReachedExactlyButNotExceeded() throws Exception {
        UUID wallet = fundedWallet("USD", 900_000);
        withdraw(wallet, 300_000, "USD", key()).andExpect(status().isCreated());
        withdraw(wallet, 200_000, "USD", key()).andExpect(status().isCreated());

        withdraw(wallet, 1, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));
        assertThat(balanceOf(wallet)).isEqualTo(400_000);
    }

    @Test
    void aSingleWithdrawalOverTheDailyLimitIsRejected() throws Exception {
        UUID wallet = fundedWallet("USD", 900_000);
        withdraw(wallet, 500_001, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));
    }

    @Test
    void theDailyLimitUsesARollingWindowThatExpiresAtExactly24Hours() throws Exception {
        UUID wallet = fundedWallet("USD", 700_000);
        deposit(wallet, 700_000, "USD", key()).andExpect(status().isCreated());
        withdraw(wallet, 500_000, "USD", key()).andExpect(status().isCreated());

        clock.advance(Duration.ofHours(24).minusSeconds(1));
        withdraw(wallet, 1, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));

        clock.advance(Duration.ofSeconds(1));
        withdraw(wallet, 500_000, "USD", key()).andExpect(status().isCreated());
        assertThat(balanceOf(wallet)).isEqualTo(400_000);
    }

    @Test
    void theDailyLimitIsTrackedPerWallet() throws Exception {
        UUID first = fundedWallet("USD", 900_000);
        UUID second = fundedWallet("USD", 900_000);
        withdraw(first, 500_000, "USD", key()).andExpect(status().isCreated());
        withdraw(second, 500_000, "USD", key()).andExpect(status().isCreated());
    }

    @Test
    void depositsAndTransfersDoNotCountTowardsTheWithdrawalLimit() throws Exception {
        UUID wallet = fundedWallet("USD", 900_000);
        UUID other = createWallet("USD");
        transferOk(wallet, other, 400_000, "USD");
        withdraw(wallet, 400_000, "USD", key()).andExpect(status().isCreated());
    }

    // ---- status changes ----

    @Test
    void freezingAWalletIsPersisted() throws Exception {
        UUID wallet = createWallet("USD");
        setStatus(wallet, "FROZEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
        getWallet(wallet).andExpect(jsonPath("$.status").value("FROZEN"));
    }

    @Test
    void aFrozenWalletCanBeReactivated() throws Exception {
        UUID wallet = createWallet("USD");
        setStatus(wallet, "FROZEN").andExpect(status().isOk());
        setStatus(wallet, "ACTIVE").andExpect(status().isOk());
        getWallet(wallet).andExpect(jsonPath("$.status").value("ACTIVE"));
        deposit(wallet, 100, "USD", key()).andExpect(status().isCreated());
    }

    @Test
    void anEmptyWalletCanBeClosed() throws Exception {
        UUID wallet = createWallet("USD");
        setStatus(wallet, "CLOSED").andExpect(status().isOk());
        getWallet(wallet).andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void aWalletWithFundsCannotBeClosed() throws Exception {
        UUID wallet = fundedWallet("USD", 100);
        setStatus(wallet, "CLOSED")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_EMPTY"));
        getWallet(wallet).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void aClosedWalletCannotBeReopened() throws Exception {
        UUID wallet = createWallet("USD");
        setStatus(wallet, "CLOSED").andExpect(status().isOk());
        setStatus(wallet, "ACTIVE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void systemWalletsCannotBeFrozen() throws Exception {
        setStatus(feeWallet("USD").getId(), "FROZEN")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYSTEM_WALLET_NOT_ALLOWED"));
    }

    @Test
    void anUnknownStatusValueIsABadRequest() throws Exception {
        UUID wallet = createWallet("USD");
        setStatus(wallet, "EXPLODED").andExpect(status().isBadRequest());
    }
}
