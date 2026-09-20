package com.example.wallet.service;

import com.example.wallet.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real multi-threaded tests: many requests hit the same wallets at the same moment. They verify
 * that locking, idempotency and the ledger stay correct under contention.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class ConcurrencyTest extends ApiTest {

    private static final int THREADS = 16;

    /** Bodies of responses that were neither a success nor a business-rule rejection (e.g. 409/500). */
    private final List<String> unexpected = new CopyOnWriteArrayList<>();

    private int statusOf(ResultActions actions) throws Exception {
        MockHttpServletResponse response = actions.andReturn().getResponse();
        int status = response.getStatus();
        if (status != 201 && status != 422) {
            unexpected.add(status + " " + response.getContentAsString());
        }
        return status;
    }

    /** Runs all tasks at the same instant and returns the HTTP status of each. */
    private List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private long count(List<Integer> statuses, int status) {
        return statuses.stream().filter(s -> s == status).count();
    }

    @Test
    void concurrentDepositsAreAllApplied() throws Exception {
        UUID wallet = createWallet("USD");
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            tasks.add(() -> statusOf(deposit(wallet, 100, "USD", key())));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(count(statuses, 201)).as(() -> unexpected.toString()).isEqualTo(40);
        assertThat(balanceOf(wallet)).isEqualTo(4_000);
        assertThat(ledgerSum(wallet)).isEqualTo(4_000);
    }

    @Test
    void transfersInOppositeDirectionsDoNotDeadlock() throws Exception {
        UUID a = fundedWallet("USD", 100_000);
        UUID b = fundedWallet("USD", 100_000);
        long feesBefore = feeWallet("USD").getBalanceMinor();

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            tasks.add(() -> statusOf(transfer(a, b, 100, "USD", key())));
            tasks.add(() -> statusOf(transfer(b, a, 100, "USD", key())));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(count(statuses, 201)).as(() -> unexpected.toString()).isEqualTo(400);
        long feesCollected = feeWallet("USD").getBalanceMinor() - feesBefore;
        assertThat(feesCollected).isEqualTo(400);
        assertThat(balanceOf(a) + balanceOf(b) + feesCollected).isEqualTo(200_000);
        assertThat(balanceOf(a)).isEqualTo(ledgerSum(a));
        assertThat(balanceOf(b)).isEqualTo(ledgerSum(b));
    }

    @Test
    void concurrentTransfersCanNeverOverdrawTheSource() throws Exception {
        UUID source = fundedWallet("USD", 1_000);
        UUID target = createWallet("USD");

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            tasks.add(() -> statusOf(transfer(source, target, 100, "USD", key())));
        }

        List<Integer> statuses = runConcurrently(tasks);

        long succeeded = count(statuses, 201);
        assertThat(unexpected).isEmpty();
        assertThat(succeeded).isEqualTo(9);                       // 9 * (100 + 1 fee) = 909 <= 1000 < 10 * 101
        assertThat(count(statuses, 422)).isEqualTo(21);
        assertThat(balanceOf(source)).isEqualTo(1_000 - succeeded * 101);
        assertThat(balanceOf(target)).isEqualTo(succeeded * 100);
    }

    @Test
    void duplicateRequestsWithTheSameKeyExecuteExactlyOnce() throws Exception {
        UUID wallet = createWallet("USD");
        String sharedKey = key();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tasks.add(() -> statusOf(deposit(wallet, 250, "USD", sharedKey)));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(count(statuses, 201)).as(() -> unexpected.toString()).isEqualTo(12);
        assertThat(balanceOf(wallet)).isEqualTo(250);
        assertThat(ledgerSum(wallet)).isEqualTo(250);
    }

    @Test
    void duplicateTransfersWithTheSameKeyMoveMoneyOnce() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = createWallet("USD");
        String sharedKey = key();

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> statusOf(transfer(source, target, 1_000, "USD", sharedKey)));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(count(statuses, 201)).as(() -> unexpected.toString()).isEqualTo(10);
        assertThat(balanceOf(target)).isEqualTo(1_000);
        assertThat(balanceOf(source)).isEqualTo(10_000 - 1_005);
    }

    @Test
    void concurrentRefundsNeverExceedTheTransferAmount() throws Exception {
        UUID source = fundedWallet("USD", 10_000);
        UUID target = createWallet("USD");
        UUID transferId = transferOk(source, target, 1_000, "USD");

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> statusOf(refund(transferId, 300, key())));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(unexpected).isEmpty();
        assertThat(count(statuses, 201)).isEqualTo(3);            // 3 * 300 = 900 <= 1000 < 4 * 300
        assertThat(count(statuses, 422)).isEqualTo(7);
        assertThat(transferRepository.findById(transferId).orElseThrow().getRefundedMinor()).isEqualTo(900);
        assertThat(balanceOf(target)).isEqualTo(100);
        assertThat(balanceOf(source)).isEqualTo(10_000 - 1_005 + 900);
    }

    @Test
    void concurrentWithdrawalsRespectTheDailyLimit() throws Exception {
        UUID wallet = createWallet("USD");
        deposit(wallet, 1_000_000, "USD", key());
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> statusOf(withdraw(wallet, 100_000, "USD", key())));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(unexpected).isEmpty();
        assertThat(count(statuses, 201)).isEqualTo(5);            // 5 * 100_000 = the 500_000 daily limit
        assertThat(count(statuses, 422)).isEqualTo(15);
        assertThat(balanceOf(wallet)).isEqualTo(500_000);
    }

    @Test
    void ledgerStaysConsistentAfterMixedConcurrentLoad() throws Exception {
        UUID a = fundedWallet("USD", 200_000);
        UUID b = fundedWallet("USD", 200_000);
        UUID c = fundedWallet("USD", 200_000);
        UUID[] wallets = {a, b, c};
        long totalBefore = balanceOf(a) + balanceOf(b) + balanceOf(c) + feeWallet("USD").getBalanceMinor();

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            UUID from = wallets[i % 3];
            UUID to = wallets[(i + 1) % 3];
            tasks.add(() -> statusOf(transfer(from, to, 500, "USD", key())));
        }
        for (int i = 0; i < 15; i++) {
            UUID wallet = wallets[i % 3];
            tasks.add(() -> statusOf(deposit(wallet, 300, "USD", key())));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(count(statuses, 201)).as(() -> unexpected.toString()).isEqualTo(60);
        for (UUID wallet : wallets) {
            assertThat(balanceOf(wallet)).isEqualTo(ledgerSum(wallet));
        }
        assertThat(feeWallet("USD").getBalanceMinor()).isEqualTo(ledgerSum(feeWallet("USD").getId()));
        long totalAfter = balanceOf(a) + balanceOf(b) + balanceOf(c) + feeWallet("USD").getBalanceMinor();
        assertThat(totalAfter).isEqualTo(totalBefore + 15 * 300);
    }
}
