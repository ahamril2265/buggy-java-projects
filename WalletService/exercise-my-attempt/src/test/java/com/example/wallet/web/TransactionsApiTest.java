package com.example.wallet.web;

import com.example.wallet.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionsApiTest extends ApiTest {

    /** A wallet with 25 deposits of 1, 2, ..., 25 minor units, oldest first. */
    private UUID walletWith25Deposits() throws Exception {
        UUID wallet = createWallet("USD");
        for (int i = 1; i <= 25; i++) {
            deposit(wallet, i, "USD", key()).andExpect(status().isCreated());
        }
        return wallet;
    }

    @Test
    void transactionsAreListedNewestFirst() throws Exception {
        UUID wallet = walletWith25Deposits();
        mvc.perform(get("/api/v1/wallets/" + wallet + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].amountMinor").value(25))
                .andExpect(jsonPath("$.items[1].amountMinor").value(24))
                .andExpect(jsonPath("$.items[19].amountMinor").value(6));
    }

    @Test
    void defaultPageSizeIs20() throws Exception {
        UUID wallet = walletWith25Deposits();
        mvc.perform(get("/api/v1/wallets/" + wallet + "/transactions"))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void pageMetadataIsCorrectForAnUnevenLastPage() throws Exception {
        UUID wallet = walletWith25Deposits();
        mvc.perform(get("/api/v1/wallets/" + wallet + "/transactions?page=0&size=10"))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/v1/wallets/" + wallet + "/transactions?page=2&size=10"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].amountMinor").value(5))
                .andExpect(jsonPath("$.items[4].amountMinor").value(1));
    }

    @Test
    void secondPageContinuesWhereTheFirstEnded() throws Exception {
        UUID wallet = walletWith25Deposits();
        mvc.perform(get("/api/v1/wallets/" + wallet + "/transactions?page=0&size=10"))
                .andExpect(jsonPath("$.items[9].amountMinor").value(16));
        mvc.perform(get("/api/v1/wallets/" + wallet + "/transactions?page=1&size=10"))
                .andExpect(jsonPath("$.items[0].amountMinor").value(15));
    }

    @Test
    void aPageBeyondTheEndIsEmptyNotAnError() throws Exception {
        UUID wallet = walletWith25Deposits();
        mvc.perform(get("/api/v1/wallets/" + wallet + "/transactions?page=9&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(25));
    }

    @Test
    void anEmptyWalletHasNoTransactions() throws Exception {
        UUID wallet = createWallet("USD");
        mvc.perform(get("/api/v1/wallets/" + wallet + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=0", "size=101", "size=100000", "size=-1", "page=-1"})
    void invalidPagingParametersAreRejected(String query) throws Exception {
        UUID wallet = createWallet("USD");
        mvc.perform(get("/api/v1/wallets/" + wallet + "/transactions?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void theMaximumPageSizeIsAccepted() throws Exception {
        UUID wallet = createWallet("USD");
        mvc.perform(get("/api/v1/wallets/" + wallet + "/transactions?size=100"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownWalletReturns404() throws Exception {
        mvc.perform(get("/api/v1/wallets/" + UUID.randomUUID() + "/transactions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    void aMalformedWalletIdIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/wallets/not-a-uuid/transactions")).andExpect(status().isBadRequest());
    }

    @Test
    void transfersShowUpAsSeparateSignedLines() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = createWallet("USD");
        transferOk(source, target, 1_000, "USD");

        mvc.perform(get("/api/v1/wallets/" + source + "/transactions"))
                .andExpect(jsonPath("$.items[0].type").value("FEE"))
                .andExpect(jsonPath("$.items[0].amountMinor").value(-5))
                .andExpect(jsonPath("$.items[1].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.items[1].amountMinor").value(-1_000))
                .andExpect(jsonPath("$.items[1].balanceAfterMinor").value(9_000))
                .andExpect(jsonPath("$.items[0].balanceAfterMinor").value(8_995));
        mvc.perform(get("/api/v1/wallets/" + target + "/transactions"))
                .andExpect(jsonPath("$.items[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.items[0].amountMinor").value(1_000));
    }
}
