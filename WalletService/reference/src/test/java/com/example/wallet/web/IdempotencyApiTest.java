package com.example.wallet.web;

import com.example.wallet.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyApiTest extends ApiTest {

    @Test
    void moneyMovingEndpointsRequireAnIdempotencyKey() throws Exception {
        UUID wallet = fundedWallet("USD", 1_000);
        UUID other = createWallet("USD");
        UUID transferId = transferOk(wallet, other, 100, "USD");

        deposit(wallet, 10, "USD", null).andExpect(status().isBadRequest());
        withdraw(wallet, 10, "USD", null).andExpect(status().isBadRequest());
        transfer(wallet, other, 10, "USD", null).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/transfers/" + transferId + "/refunds")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountMinor\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aBlankOrOverlongKeyIsRejected() throws Exception {
        UUID wallet = createWallet("USD");
        deposit(wallet, 10, "USD", " ").andExpect(status().isBadRequest());
        deposit(wallet, 10, "USD", "k".repeat(65)).andExpect(status().isBadRequest());
        deposit(wallet, 10, "USD", "k".repeat(64)).andExpect(status().isCreated());
    }

    @Test
    void theFirstRequestIsNotMarkedAsReplayed() throws Exception {
        UUID wallet = createWallet("USD");
        MvcResult result = deposit(wallet, 100, "USD", key()).andExpect(status().isCreated()).andReturn();
        assertThat(result.getResponse().getHeader("Idempotent-Replayed")).isNull();
    }

    @Test
    void retryingWithTheSameKeyReplaysTheOriginalResponse() throws Exception {
        UUID wallet = createWallet("USD");
        String key = key();

        MvcResult first = deposit(wallet, 700, "USD", key).andExpect(status().isCreated()).andReturn();
        MvcResult second = deposit(wallet, 700, "USD", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(balanceOf(wallet)).isEqualTo(700);
        assertThat(ledgerRepository.findByWalletId(wallet, org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void aReplayedWithdrawalDoesNotWithdrawTwice() throws Exception {
        UUID wallet = fundedWallet("USD", 1_000);
        String key = key();
        withdraw(wallet, 300, "USD", key).andExpect(status().isCreated());
        withdraw(wallet, 300, "USD", key).andExpect(status().isCreated());
        withdraw(wallet, 300, "USD", key).andExpect(status().isCreated());
        assertThat(balanceOf(wallet)).isEqualTo(700);
    }

    @Test
    void aReplayedTransferDoesNotChargeTwice() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = createWallet("USD");
        String key = key();

        String firstId = field(transfer(source, target, 1_000, "USD", key).andExpect(status().isCreated()), "$.id");
        String replayId = field(transfer(source, target, 1_000, "USD", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true")), "$.id");

        assertThat(replayId).isEqualTo(firstId);
        assertThat(balanceOf(source)).isEqualTo(8_995);
        assertThat(balanceOf(target)).isEqualTo(1_000);
    }

    @Test
    void reusingAKeyWithADifferentAmountIsRejected() throws Exception {
        UUID wallet = createWallet("USD");
        String key = key();
        deposit(wallet, 100, "USD", key).andExpect(status().isCreated());

        deposit(wallet, 999, "USD", key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(balanceOf(wallet)).isEqualTo(100);
    }

    @Test
    void reusingAKeyForADifferentWalletIsRejected() throws Exception {
        UUID first = createWallet("USD");
        UUID second = createWallet("USD");
        String key = key();
        deposit(first, 100, "USD", key).andExpect(status().isCreated());

        deposit(second, 100, "USD", key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(balanceOf(second)).isZero();
    }

    @Test
    void reusingAKeyForADifferentOperationIsRejected() throws Exception {
        UUID wallet = fundedWallet("USD", 1_000);
        String key = key();
        deposit(wallet, 100, "USD", key).andExpect(status().isCreated());

        withdraw(wallet, 100, "USD", key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void reusingAKeyForADifferentTransferTargetIsRejected() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID targetA = createWallet("USD");
        UUID targetB = createWallet("USD");
        String key = key();
        transfer(source, targetA, 100, "USD", key).andExpect(status().isCreated());

        transfer(source, targetB, 100, "USD", key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void failedRequestsAreNotCachedSoTheKeyCanBeRetried() throws Exception {
        UUID wallet = createWallet("USD");
        String key = key();

        withdraw(wallet, 500, "USD", key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        deposit(wallet, 1_000, "USD", key()).andExpect(status().isCreated());

        withdraw(wallet, 500, "USD", key).andExpect(status().isCreated());
        assertThat(balanceOf(wallet)).isEqualTo(500);
    }

    @Test
    void validationFailuresDoNotConsumeTheKey() throws Exception {
        UUID wallet = createWallet("USD");
        String key = key();
        deposit(wallet, -5, "USD", key).andExpect(status().isBadRequest());
        deposit(wallet, 5, "USD", key).andExpect(status().isCreated());
    }

    @Test
    void refundsAreIdempotentToo() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = createWallet("USD");
        UUID transferId = transferOk(source, target, 1_000, "USD");
        String key = key();

        refund(transferId, 400, key).andExpect(status().isCreated());
        refund(transferId, 400, key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(balanceOf(target)).isEqualTo(600);
    }
}
