package com.example.wallet.support;

import com.example.wallet.domain.LedgerEntry;
import com.example.wallet.domain.Wallet;
import com.example.wallet.repository.LedgerEntryRepository;
import com.example.wallet.repository.TransferRepository;
import com.example.wallet.repository.WalletRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base class: full application context, MockMvc, a controllable clock and small API helpers. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
public abstract class ApiTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.configure(registry);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected MutableClock clock;
    @Autowired protected WalletRepository walletRepository;
    @Autowired protected LedgerEntryRepository ledgerRepository;
    @Autowired protected TransferRepository transferRepository;

    @BeforeEach
    void resetClock() {
        clock.set(TestClockConfig.BASE_TIME);
    }

    protected static String key() {
        return UUID.randomUUID().toString();
    }

    protected UUID createWallet(String currency) throws Exception {
        String ownerId = "owner-" + UUID.randomUUID();
        return UUID.fromString(field(createWalletRaw(ownerId, currency).andExpect(status().isCreated()), "$.id"));
    }

    protected ResultActions createWalletRaw(String ownerId, String currency) throws Exception {
        return mvc.perform(post("/api/v1/wallets").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("ownerId", ownerId, "currency", currency))));
    }

    protected UUID fundedWallet(String currency, long amountMinor) throws Exception {
        UUID id = createWallet(currency);
        deposit(id, amountMinor, currency, key()).andExpect(status().isCreated());
        return id;
    }

    protected ResultActions deposit(UUID walletId, long amountMinor, String currency, String idempotencyKey) throws Exception {
        return money("/api/v1/wallets/" + walletId + "/deposits", amountMinor, currency, idempotencyKey);
    }

    protected ResultActions withdraw(UUID walletId, long amountMinor, String currency, String idempotencyKey) throws Exception {
        return money("/api/v1/wallets/" + walletId + "/withdrawals", amountMinor, currency, idempotencyKey);
    }

    private ResultActions money(String url, long amountMinor, String currency, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("amountMinor", amountMinor, "currency", currency)));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    protected ResultActions transfer(UUID source, UUID target, long amountMinor, String currency, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("sourceWalletId", source.toString(),
                        "targetWalletId", target.toString(), "amountMinor", amountMinor, "currency", currency)));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    protected ResultActions refund(UUID transferId, long amountMinor, String idempotencyKey) throws Exception {
        return mvc.perform(post("/api/v1/transfers/" + transferId + "/refunds")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .content(json.writeValueAsString(Map.of("amountMinor", amountMinor))));
    }

    protected ResultActions setStatus(UUID walletId, String status) throws Exception {
        return mvc.perform(patch("/api/v1/wallets/" + walletId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", status))));
    }

    protected ResultActions getWallet(UUID walletId) throws Exception {
        return mvc.perform(get("/api/v1/wallets/" + walletId));
    }

    protected String field(ResultActions result, String jsonPath) throws Exception {
        Object value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath);
        return String.valueOf(value);
    }

    protected UUID transferOk(UUID source, UUID target, long amountMinor, String currency) throws Exception {
        return UUID.fromString(field(transfer(source, target, amountMinor, currency, key())
                .andExpect(status().isCreated()), "$.id"));
    }

    protected long balanceOf(UUID walletId) {
        return walletRepository.findById(walletId).orElseThrow().getBalanceMinor();
    }

    protected Wallet feeWallet(String currency) {
        return walletRepository.findByOwnerIdAndCurrency(Wallet.SYSTEM_OWNER, currency).orElseThrow();
    }

    protected long ledgerSum(UUID walletId) {
        return ledgerRepository.sumByWalletId(walletId);
    }

    protected List<LedgerEntry> entriesOf(UUID transactionId) {
        return ledgerRepository.findByTransactionId(transactionId);
    }
}
