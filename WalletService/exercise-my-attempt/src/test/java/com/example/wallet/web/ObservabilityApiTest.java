package com.example.wallet.web;

import com.example.wallet.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ObservabilityApiTest extends ApiTest {

    @Test
    void aSuppliedCorrelationIdIsEchoed() throws Exception {
        mvc.perform(get("/api/v1/wallets/" + UUID.randomUUID()).header("X-Correlation-Id", "req-12345.abc"))
                .andExpect(header().string("X-Correlation-Id", "req-12345.abc"));
    }

    @Test
    void aCorrelationIdIsGeneratedWhenMissing() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/wallets/" + UUID.randomUUID())).andReturn();
        String id = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(id).isNotBlank();
        UUID.fromString(id);
    }

    @Test
    void anUnsafeCorrelationIdIsReplacedNotEchoed() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/wallets/" + UUID.randomUUID())
                .header("X-Correlation-Id", "bad id\twith spaces")).andReturn();
        assertThat(result.getResponse().getHeader("X-Correlation-Id")).doesNotContain(" ");

        MvcResult tooLong = mvc.perform(get("/api/v1/wallets/" + UUID.randomUUID())
                .header("X-Correlation-Id", "a".repeat(65))).andReturn();
        assertThat(tooLong.getResponse().getHeader("X-Correlation-Id")).hasSize(36);
    }

    @Test
    void errorBodiesCarryTheCorrelationId() throws Exception {
        mvc.perform(get("/api/v1/wallets/" + UUID.randomUUID()).header("X-Correlation-Id", "trace-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").value("trace-1"));
    }

    @Test
    void theCorrelationIdDoesNotLeakIntoLaterWorkOnTheSameThread() throws Exception {
        mvc.perform(get("/api/v1/wallets/" + UUID.randomUUID()).header("X-Correlation-Id", "trace-2"));
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void errorsUseTheProblemJsonFormat() throws Exception {
        mvc.perform(get("/api/v1/wallets/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("WALLET_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(containsString("Wallet not found")))
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    void validationErrorsListEveryOffendingField() throws Exception {
        mvc.perform(post("/api/v1/wallets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"\",\"currency\":\"dollars\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void malformedJsonIsABadRequestNotAServerError() throws Exception {
        mvc.perform(post("/api/v1/wallets").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void anUnsupportedMediaTypeIsRejected() throws Exception {
        mvc.perform(post("/api/v1/wallets").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void errorResponsesNeverLeakInternals() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/wallets").contentType(MediaType.APPLICATION_JSON)
                .content("{ not json")).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("com.example").doesNotContain("Exception").doesNotContain("at ");
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void transferMetricsArePublished() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = createWallet("USD");
        transferOk(source, target, 1_000, "USD");

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("wallet_transfers_total")));
    }
}
