package com.example.wallet.web;

import com.example.wallet.domain.EntryType;
import com.example.wallet.domain.LedgerEntry;
import com.example.wallet.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferApiTest extends ApiTest {

    // ---- the happy path and the money trail ----

    @Test
    void aTransferMovesTheAmountAndTheSenderPaysTheFee() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = createWallet("USD");
        long feeWalletBefore = feeWallet("USD").getBalanceMinor();

        transfer(source, target, 2_000, "USD", key())
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.amountMinor").value(2_000))
                .andExpect(jsonPath("$.feeMinor").value(10))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.refundedMinor").value(0));

        assertThat(balanceOf(source)).isEqualTo(10_000 - 2_000 - 10);
        assertThat(balanceOf(target)).isEqualTo(2_000);
        assertThat(feeWallet("USD").getBalanceMinor()).isEqualTo(feeWalletBefore + 10);
    }

    @Test
    void feeIsRoundedHalfUpAndHasAMinimum() throws Exception {
        UUID source = fundedWallet("USD", 100_000);
        UUID target = createWallet("USD");

        transfer(source, target, 1_100, "USD", key()).andExpect(jsonPath("$.feeMinor").value(6));
        transfer(source, target, 10, "USD", key()).andExpect(jsonPath("$.feeMinor").value(1));
        transfer(source, target, 1_099, "USD", key()).andExpect(jsonPath("$.feeMinor").value(5));
    }

    @Test
    void everyTransfersLedgerEntriesSumToZero() throws Exception {
        UUID source = fundedWallet("USD", 50_000);
        UUID target = createWallet("USD");
        UUID transferId = transferOk(source, target, 12_345, "USD");

        List<LedgerEntry> entries = ledgerRepository.findByTransferIdOrderByIdAsc(transferId);
        assertThat(entries).extracting(LedgerEntry::getType)
                .containsExactlyInAnyOrder(EntryType.TRANSFER_OUT, EntryType.FEE, EntryType.TRANSFER_IN,
                        EntryType.FEE_INCOME);
        assertThat(entries.stream().mapToLong(LedgerEntry::getAmountMinor).sum()).isZero();
        assertThat(entries.stream().map(LedgerEntry::getTransactionId).distinct()).hasSize(1);
    }

    @Test
    void walletBalancesAlwaysEqualTheSumOfTheirLedgerEntries() throws Exception {
        UUID a = fundedWallet("USD", 50_000);
        UUID b = fundedWallet("USD", 20_000);
        transferOk(a, b, 4_321, "USD");
        transferOk(b, a, 777, "USD");
        withdraw(a, 1_000, "USD", key()).andExpect(status().isCreated());

        assertThat(balanceOf(a)).isEqualTo(ledgerSum(a));
        assertThat(balanceOf(b)).isEqualTo(ledgerSum(b));
        assertThat(feeWallet("USD").getBalanceMinor()).isEqualTo(ledgerSum(feeWallet("USD").getId()));
    }

    @Test
    void balanceAfterOnEachLedgerLineReflectsTheRunningBalance() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = createWallet("USD");
        UUID transferId = transferOk(source, target, 1_000, "USD");

        List<LedgerEntry> sourceLines = ledgerRepository.findByTransferIdOrderByIdAsc(transferId).stream()
                .filter(e -> e.getWalletId().equals(source)).toList();
        assertThat(sourceLines).extracting(LedgerEntry::getBalanceAfterMinor).containsExactly(9_000L, 8_995L);
    }

    @Test
    void totalMoneyIsConservedByATransfer() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = fundedWallet("USD", 3_000);
        long feeBefore = feeWallet("USD").getBalanceMinor();
        long totalBefore = balanceOf(source) + balanceOf(target) + feeBefore;

        transferOk(source, target, 2_500, "USD");

        long totalAfter = balanceOf(source) + balanceOf(target) + feeWallet("USD").getBalanceMinor();
        assertThat(totalAfter).isEqualTo(totalBefore);
    }

    @Test
    void feesAreCollectedInTheFeeWalletOfTheTransferCurrency() throws Exception {
        UUID source = fundedWallet("EUR", 10_000);
        UUID target = createWallet("EUR");
        long eurBefore = feeWallet("EUR").getBalanceMinor();
        long usdBefore = feeWallet("USD").getBalanceMinor();

        transferOk(source, target, 2_000, "EUR");

        assertThat(feeWallet("EUR").getBalanceMinor()).isEqualTo(eurBefore + 10);
        assertThat(feeWallet("USD").getBalanceMinor()).isEqualTo(usdBefore);
    }

    // ---- funds and limits ----

    @Test
    void theSenderMustAffordTheAmountPlusTheFee() throws Exception {
        UUID source = fundedWallet("USD", 1_000);
        UUID target = createWallet("USD");

        transfer(source, target, 1_000, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(balanceOf(source)).isEqualTo(1_000);
        assertThat(balanceOf(target)).isZero();
    }

    @Test
    void aTransferThatUsesExactlyTheWholeBalanceSucceeds() throws Exception {
        UUID source = fundedWallet("USD", 1_005);
        UUID target = createWallet("USD");

        transfer(source, target, 1_000, "USD", key()).andExpect(status().isCreated());
        assertThat(balanceOf(source)).isZero();
    }

    @Test
    void aTransferAboveThePerTransactionLimitIsRejected() throws Exception {
        UUID source = createWallet("USD");
        UUID target = createWallet("USD");
        transfer(source, target, 1_000_001, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AMOUNT_LIMIT_EXCEEDED"));
    }

    // ---- rejected transfers ----

    @Test
    void aWalletCannotTransferToItself() throws Exception {
        UUID wallet = fundedWallet("USD", 10_000);
        transfer(wallet, wallet, 100, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_WALLET_TRANSFER"));
        assertThat(balanceOf(wallet)).isEqualTo(10_000);
    }

    @Test
    void systemWalletsCannotBeUsedAsSourceOrTarget() throws Exception {
        UUID user = fundedWallet("USD", 10_000);
        UUID system = feeWallet("USD").getId();

        transfer(system, user, 1, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYSTEM_WALLET_NOT_ALLOWED"));
        transfer(user, system, 100, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYSTEM_WALLET_NOT_ALLOWED"));
        assertThat(balanceOf(user)).isEqualTo(10_000);
    }

    @Test
    void unknownWalletsReturn404() throws Exception {
        UUID known = fundedWallet("USD", 1_000);
        transfer(UUID.randomUUID(), known, 100, "USD", key())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
        transfer(known, UUID.randomUUID(), 100, "USD", key())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
        assertThat(balanceOf(known)).isEqualTo(1_000);
    }

    @Test
    void walletsInDifferentCurrenciesCannotTransferToEachOther() throws Exception {
        UUID usd = fundedWallet("USD", 10_000);
        UUID eur = createWallet("EUR");
        transfer(usd, eur, 100, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
        assertThat(balanceOf(usd)).isEqualTo(10_000);
    }

    @Test
    void theRequestCurrencyMustMatchTheWallets() throws Exception {
        UUID a = fundedWallet("USD", 10_000);
        UUID b = createWallet("USD");
        transfer(a, b, 100, "EUR", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void anUnsupportedCurrencyIsRejected() throws Exception {
        UUID a = createWallet("USD");
        UUID b = createWallet("USD");
        transfer(a, b, 100, "JPY", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
    }

    @Test
    void aFrozenTargetRejectsTheTransferAndTheSourceIsUntouched() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = createWallet("USD");
        setStatus(target, "FROZEN").andExpect(status().isOk());
        long entriesBefore = ledgerRepository.findByWalletId(source, Pageable.unpaged()).getTotalElements();
        long feeBefore = feeWallet("USD").getBalanceMinor();

        transfer(source, target, 1_000, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));

        assertThat(balanceOf(source)).isEqualTo(10_000);
        assertThat(balanceOf(target)).isZero();
        assertThat(feeWallet("USD").getBalanceMinor()).isEqualTo(feeBefore);
        assertThat(ledgerRepository.findByWalletId(source, Pageable.unpaged()).getTotalElements())
                .isEqualTo(entriesBefore);
    }

    @Test
    void aFrozenSourceCannotSend() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = createWallet("USD");
        setStatus(source, "FROZEN").andExpect(status().isOk());
        transfer(source, target, 100, "USD", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));
    }

    @Test
    void aRejectedTransferLeavesNoTransferRecord() throws Exception {
        UUID source = createWallet("USD");
        UUID target = createWallet("USD");
        long before = transferRepository.count();
        transfer(source, target, 100, "USD", key()).andExpect(status().isUnprocessableEntity());
        assertThat(transferRepository.count()).isEqualTo(before);
    }

    @Test
    void invalidRequestBodiesAreBadRequests() throws Exception {
        UUID a = createWallet("USD");
        UUID b = createWallet("USD");
        transfer(a, b, 0, "USD", key())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amountMinor"));
        transfer(a, b, -10, "USD", key()).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key())
                        .content("{\"sourceWalletId\":\"nope\",\"targetWalletId\":\"" + b + "\","
                                + "\"amountMinor\":5,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- reading transfers ----

    @Test
    void aTransferCanBeReadBack() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = createWallet("USD");
        UUID transferId = transferOk(source, target, 1_000, "USD");

        mvc.perform(get("/api/v1/transfers/" + transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transferId.toString()))
                .andExpect(jsonPath("$.sourceWalletId").value(source.toString()))
                .andExpect(jsonPath("$.targetWalletId").value(target.toString()))
                .andExpect(jsonPath("$.amountMinor").value(1_000))
                .andExpect(jsonPath("$.feeMinor").value(5));
    }

    @Test
    void anUnknownTransferReturns404() throws Exception {
        mvc.perform(get("/api/v1/transfers/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
    }
}
