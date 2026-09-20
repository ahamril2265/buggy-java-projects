package com.example.wallet.web;

import com.example.wallet.domain.EntryType;
import com.example.wallet.domain.LedgerEntry;
import com.example.wallet.support.ApiTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefundApiTest extends ApiTest {

    private UUID source;
    private UUID target;
    private UUID transferId;

    /** source pays 1_000 (+5 fee) to target. */
    private void givenATransferOf1000() throws Exception {
        source = fundedWallet("USD", 10_000);
        target = createWallet("USD");
        transferId = transferOk(source, target, 1_000, "USD");
    }

    @Test
    void aPartialRefundReturnsMoneyToTheSenderButNotTheFee() throws Exception {
        givenATransferOf1000();

        refund(transferId, 400, key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundedNowMinor").value(400))
                .andExpect(jsonPath("$.totalRefundedMinor").value(400))
                .andExpect(jsonPath("$.refundableMinor").value(600))
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));

        assertThat(balanceOf(target)).isEqualTo(600);
        assertThat(balanceOf(source)).isEqualTo(10_000 - 1_000 - 5 + 400);
    }

    @Test
    void refundsAccumulateAndTheLastOneMarksTheTransferRefunded() throws Exception {
        givenATransferOf1000();

        refund(transferId, 250, key()).andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));
        refund(transferId, 250, key()).andExpect(jsonPath("$.totalRefundedMinor").value(500));
        refund(transferId, 500, key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRefundedMinor").value(1_000))
                .andExpect(jsonPath("$.refundableMinor").value(0))
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        assertThat(balanceOf(target)).isZero();
        assertThat(balanceOf(source)).isEqualTo(10_000 - 5);
        mvc.perform(get("/api/v1/transfers/" + transferId))
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundedMinor").value(1_000));
    }

    @Test
    void aFullRefundInOneGoMarksTheTransferRefunded() throws Exception {
        givenATransferOf1000();
        refund(transferId, 1_000, key()).andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void refundsCannotExceedTheTransferAmountInTotal() throws Exception {
        givenATransferOf1000();
        refund(transferId, 600, key()).andExpect(status().isCreated());

        refund(transferId, 401, key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_TRANSFER"));

        assertThat(balanceOf(target)).isEqualTo(400);
        mvc.perform(get("/api/v1/transfers/" + transferId))
                .andExpect(jsonPath("$.refundedMinor").value(600))
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));
    }

    @Test
    void aSingleRefundLargerThanTheTransferIsRejected() throws Exception {
        givenATransferOf1000();
        refund(transferId, 1_001, key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_TRANSFER"));
    }

    @Test
    void aFullyRefundedTransferCannotBeRefundedAgain() throws Exception {
        givenATransferOf1000();
        refund(transferId, 1_000, key()).andExpect(status().isCreated());
        refund(transferId, 1, key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_TRANSFER"));
    }

    @Test
    void aRefundFailsIfTheReceiverHasAlreadySpentTheMoney() throws Exception {
        givenATransferOf1000();
        withdraw(target, 900, "USD", key()).andExpect(status().isCreated());

        refund(transferId, 500, key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        mvc.perform(get("/api/v1/transfers/" + transferId))
                .andExpect(jsonPath("$.refundedMinor").value(0))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(balanceOf(source)).isEqualTo(10_000 - 1_000 - 5);
    }

    @Test
    void anUnknownTransferReturns404() throws Exception {
        refund(UUID.randomUUID(), 10, key())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
    }

    @Test
    void aNonPositiveRefundIsABadRequest() throws Exception {
        givenATransferOf1000();
        refund(transferId, 0, key()).andExpect(status().isBadRequest());
        refund(transferId, -5, key()).andExpect(status().isBadRequest());
    }

    @Test
    void aRefundLeavesABalancedLedgerTrail() throws Exception {
        givenATransferOf1000();
        refund(transferId, 300, key()).andExpect(status().isCreated());

        List<LedgerEntry> entries = ledgerRepository.findByTransferIdOrderByIdAsc(transferId);
        List<LedgerEntry> refundEntries = entries.stream()
                .filter(e -> e.getType() == EntryType.REFUND_OUT || e.getType() == EntryType.REFUND_IN).toList();

        assertThat(refundEntries).hasSize(2);
        assertThat(refundEntries.stream().mapToLong(LedgerEntry::getAmountMinor).sum()).isZero();
        assertThat(entries.stream().mapToLong(LedgerEntry::getAmountMinor).sum()).isZero();
        assertThat(balanceOf(source)).isEqualTo(ledgerSum(source));
        assertThat(balanceOf(target)).isEqualTo(ledgerSum(target));
    }

    @Test
    void refundsConserveTotalMoney() throws Exception {
        givenATransferOf1000();
        long total = balanceOf(source) + balanceOf(target) + feeWallet("USD").getBalanceMinor();
        refund(transferId, 700, key()).andExpect(status().isCreated());
        assertThat(balanceOf(source) + balanceOf(target) + feeWallet("USD").getBalanceMinor()).isEqualTo(total);
    }

    @Test
    void aRefundIntoAFrozenSourceIsRejectedAndNothingMoves() throws Exception {
        givenATransferOf1000();
        setStatus(source, "FROZEN").andExpect(status().isOk());

        refund(transferId, 100, key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));

        assertThat(balanceOf(target)).isEqualTo(1_000);
        mvc.perform(get("/api/v1/transfers/" + transferId)).andExpect(jsonPath("$.refundedMinor").value(0));
    }
}
