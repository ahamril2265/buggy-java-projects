import com.orchestrator.model.Job;
import com.orchestrator.model.JobEvent;
import com.orchestrator.model.JobStatus;
import com.orchestrator.service.CostCalculator;
import com.orchestrator.service.DailySchedule;
import com.orchestrator.service.DependencyResolver;
import com.orchestrator.service.EventBus;
import com.orchestrator.service.JobMetrics;
import com.orchestrator.service.JobRegistry;
import com.orchestrator.service.JobReport;
import com.orchestrator.service.JobSpecParser;
import com.orchestrator.service.JobTask;
import com.orchestrator.service.PriorityJobQueue;
import com.orchestrator.service.RateLimiter;
import com.orchestrator.service.ResultCache;
import com.orchestrator.service.RetryPolicy;
import com.orchestrator.service.Scheduler;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Scenario runner for the Job Orchestration Engine.
 * Each scenario prints PASS, FAIL or CRASHED. A FAIL/CRASHED means there is
 * a bug in the corresponding model/service code. Do not edit the assertions.
 */
public class Main {

    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) {
        System.out.println("=== Job Orchestration Engine - Scenario Runner ===\n");

        scenario1_diamondDependenciesResolve();
        scenario2_realCycleDetected();
        scenario3_queueHandlesExtremePriorities();
        scenario4_queueIsFifoForEqualPriorities();
        scenario5_backoffIsCappedForLargeRetryNumbers();
        scenario6_backoffDoublesThenCaps();
        scenario7_cacheEvictsLeastRecentlyUsed();
        scenario8_rateLimiterWindowBoundary();
        scenario9_registryFindsNameByValue();
        scenario10_samePriorityComparesValues();
        scenario11_costUsesExactDecimalRate();
        scenario12_metricsSurviveConcurrency();
        scenario13_jobOwnsItsDependencies();
        scenario14_eventBusIsolatesFailingListener();
        scenario15_reportKeepsSamePriorityJobs();
        scenario16_dailyScheduleSurvivesDst();
        scenario17_namesByStatusHandlesSharedStatus();
        scenario18_specParser();
        scenario19_failureSkipsAllDescendants();
        scenario20_jobRunsOnlyAfterAllDependencies();
        scenario21_jobAttemptedRetriesPlusOneTimes();
        scenario22_happyPathChain();

        System.out.println("\n=== Summary: " + passCount + " passed, " + failCount + " failed ===");
    }

    private static void scenario1_diamondDependenciesResolve() {
        run("Scenario 1: A diamond dependency shape resolves (shared dependency is not a cycle)", () -> {
            Map<String, Job> jobs = jobs(
                    job("a", 1, "b", "c"),
                    job("b", 1, "d"),
                    job("c", 1, "d"),
                    job("d", 1));

            List<String> order = new DependencyResolver().resolveOrder(jobs);

            check(order.size() == 4, "expected 4 ids in the order, got " + order);
            check(order.indexOf("d") < order.indexOf("b"), "d must come before b: " + order);
            check(order.indexOf("d") < order.indexOf("c"), "d must come before c: " + order);
            check(order.indexOf("b") < order.indexOf("a"), "b must come before a: " + order);
            check(order.indexOf("c") < order.indexOf("a"), "c must come before a: " + order);
        });
    }

    private static void scenario2_realCycleDetected() {
        run("Scenario 2: A genuine cycle is rejected", () -> {
            Map<String, Job> jobs = jobs(job("a", 1, "b"), job("b", 1, "a"));
            try {
                new DependencyResolver().resolveOrder(jobs);
                check(false, "expected IllegalStateException for the a <-> b cycle");
            } catch (IllegalStateException expected) {
                // correct
            }
        });
    }

    private static void scenario3_queueHandlesExtremePriorities() {
        run("Scenario 3: Queue orders correctly across extreme int priorities", () -> {
            PriorityJobQueue queue = new PriorityJobQueue();
            queue.submit(job("low", -5));
            queue.submit(job("high", Integer.MAX_VALUE));
            queue.submit(job("mid", 0));

            List<String> order = new ArrayList<>();
            while (queue.size() > 0) {
                Job last = queue.poll();
                //System.out.println( last );
                order.add(last.getId());
                //System.out.println( order );
            }

            check(order.equals(Arrays.asList("high", "mid", "low")), "expected [high, mid, low], got " + order);
        });
    }

    private static void scenario4_queueIsFifoForEqualPriorities() {
        run("Scenario 4: Equal priorities are served first-in-first-out", () -> {
            PriorityJobQueue queue = new PriorityJobQueue();
            queue.submit(job("x", 5));
            queue.submit(job("y", 5));
            queue.submit(job("z", 5));

            List<String> order = new ArrayList<>();
            while (queue.size() > 0) {
                order.add(queue.poll().getId());
            }

            check(order.equals(Arrays.asList("x", "y", "z")), "expected [x, y, z], got " + order);
        });
    }

    private static void scenario5_backoffIsCappedForLargeRetryNumbers() {
        run("Scenario 5: Backoff delay is capped for large retry numbers", () -> {
            RetryPolicy policy = new RetryPolicy(1000, 60000, 50);

            check(policy.delayForRetry(25) == 60000, "retry 25: expected 60000, got " + policy.delayForRetry(25));
            check(policy.delayForRetry(33) == 60000, "retry 33: expected 60000, got " + policy.delayForRetry(33));
        });
    }

    private static void scenario6_backoffDoublesThenCaps() {
        run("Scenario 6: Backoff doubles per retry, then caps", () -> {
            RetryPolicy policy = new RetryPolicy(100, 10000, 10);

            check(policy.delayForRetry(1) == 100, "retry 1: expected 100, got " + policy.delayForRetry(1));
            check(policy.delayForRetry(2) == 200, "retry 2: expected 200, got " + policy.delayForRetry(2));
            check(policy.delayForRetry(3) == 400, "retry 3: expected 400, got " + policy.delayForRetry(3));
            check(policy.delayForRetry(8) == 10000, "retry 8: expected 10000 (capped), got " + policy.delayForRetry(8));
        });
    }

    private static void scenario7_cacheEvictsLeastRecentlyUsed() {
        run("Scenario 7: Cache evicts the least-recently-used entry", () -> {
            ResultCache<String, Integer> cache = new ResultCache<>(2);
            cache.put("a", 1);
            cache.put("b", 2);
            cache.get("a");
            cache.put("c", 3);

            check(cache.containsKey("a"), "'a' was just read, it must survive eviction");
            check(!cache.containsKey("b"), "'b' is least recently used, it must be evicted");
            check(cache.size() == 2, "expected size 2, got " + cache.size());
        });
    }

    private static void scenario8_rateLimiterWindowBoundary() {
        run("Scenario 8: An event exactly one window old no longer counts", () -> {
            RateLimiter limiter = new RateLimiter(2, 1000);

            check(limiter.tryAcquire(0), "t=0 should be allowed");
            check(limiter.tryAcquire(500), "t=500 should be allowed");
            check(!limiter.tryAcquire(600), "t=600 should be rejected (2 events in window)");
            check(limiter.tryAcquire(1000), "t=1000: the t=0 event is exactly 1000ms old and has expired, so this should be allowed");
        });
    }

    private static void scenario9_registryFindsNameByValue() {
        run("Scenario 9: Registry finds a job by name value, not reference", () -> {
            JobRegistry registry = new JobRegistry();
            registry.register(new Job("j1", "build", 1, new HashSet<>()));

            String lookup = new String("build");

            check(registry.findByName(lookup).isPresent(), "expected to find the job named 'build'");
        });
    }

    private static void scenario10_samePriorityComparesValues() {
        run("Scenario 10: Equal priority values are recognised as equal", () -> {
            Job a = job("a", 10);
            Job b = job("b", 10);
            Job c = job("c", 1000);
            Job d = job("d", 1000);

            check(a.hasSamePriorityAs(b), "priorities 10 and 10 should be equal");
            check(c.hasSamePriorityAs(d), "priorities 1000 and 1000 should be equal");
            check(!a.hasSamePriorityAs(c), "priorities 10 and 1000 should differ");
        });
    }

    private static void scenario11_costUsesExactDecimalRate() {
        run("Scenario 11: Cost uses the rate as written (1.005/s * 1s = 1.01)", () -> {
            BigDecimal cost = new CostCalculator().jobCost(1.005, 1);

            check(cost.compareTo(new BigDecimal("1.01")) == 0, "expected 1.01, got " + cost);
        });
    }

    private static void scenario12_metricsSurviveConcurrency() {
        run("Scenario 12: Metrics counters do not lose updates under concurrency", () -> {
            JobMetrics metrics = new JobMetrics();
            int threadCount = 8;
            int perThread = 200_000;
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                Thread t = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    for (int n = 0; n < perThread; n++) {
                        metrics.recordCompleted();
                    }
                });
                threads.add(t);
                t.start();
            }
            start.countDown();
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            int expected = threadCount * perThread;
            check(metrics.getCompleted() == expected, "expected " + expected + " completions, got " + metrics.getCompleted());
        });
    }

    private static void scenario13_jobOwnsItsDependencies() {
        run("Scenario 13: A job's dependencies are unaffected by later changes to the caller's set", () -> {
            Set<String> deps = new HashSet<>(Arrays.asList("a"));
            Job job = new Job("j", "J", 1, deps);

            deps.add("b");

            check(job.getDependencies().size() == 1, "expected the job to keep 1 dependency, got " + job.getDependencies());
        });
    }

    private static void scenario14_eventBusIsolatesFailingListener() {
        run("Scenario 14: A failing listener does not stop other listeners", () -> {
            EventBus bus = new EventBus();
            int[] delivered = {0};
            bus.subscribe(e -> {
                throw new RuntimeException("boom");
            });
            bus.subscribe(e -> delivered[0]++);

            List<String> errors = bus.publish(new JobEvent("j", JobStatus.COMPLETED));

            check(delivered[0] == 1, "expected the second listener to run once, ran " + delivered[0] + " times");
            check(errors.size() == 1, "expected exactly 1 recorded listener failure, got " + errors.size());
        });
    }

    private static void scenario15_reportKeepsSamePriorityJobs() {
        run("Scenario 15: Sorted report keeps every job, even with equal priorities", () -> {
            List<Job> jobs = Arrays.asList(job("j2", 5), job("j1", 5), job("j3", 1));

            List<Job> sorted = new ArrayList<>(new JobReport().sortedByPriority(jobs));

            check(sorted.size() == 3, "expected all 3 jobs, got " + sorted.size());
            if (sorted.size() == 3) {
                check(sorted.get(0).getId().equals("j1"), "expected j1 first (priority 5, id order), got " + sorted.get(0).getId());
                check(sorted.get(2).getId().equals("j3"), "expected j3 last (priority 1), got " + sorted.get(2).getId());
            }
        });
    }

    private static void scenario16_dailyScheduleSurvivesDst() {
        run("Scenario 16: A 09:00 daily job stays at 09:00 across the US spring-forward change", () -> {
            ZoneId newYork = ZoneId.of("America/New_York");
            ZonedDateTime last = ZonedDateTime.of(2026, 3, 7, 9, 0, 0, 0, newYork);
            //System.out.println( last );

            ZonedDateTime next = new DailySchedule().nextRun(last);

            check(next.toLocalDate().equals(LocalDate.of(2026, 3, 8)), "expected March 8, got " + next.toLocalDate());
            check(next.getHour() == 9, "expected local hour 9, got " + next.getHour() + " (" + next + ")");
        });
    }

    private static void scenario17_namesByStatusHandlesSharedStatus() {
        run("Scenario 17: namesByStatus groups several jobs sharing a status", () -> {
            Job b = job("jb", "b", 1);
            Job a = job("ja", "a", 1);
            Job c = job("jc", "c", 1);
            b.setStatus(JobStatus.COMPLETED);
            a.setStatus(JobStatus.COMPLETED);
            c.setStatus(JobStatus.FAILED);

            Map<JobStatus, List<String>> names = new JobReport().namesByStatus(Arrays.asList(b, a, c));

            check(names.get(JobStatus.COMPLETED) != null && names.get(JobStatus.COMPLETED).equals(Arrays.asList("a", "b")),
                    "expected COMPLETED -> [a, b], got " + names.get(JobStatus.COMPLETED));
            check(names.get(JobStatus.FAILED) != null && names.get(JobStatus.FAILED).equals(Arrays.asList("c")),
                    "expected FAILED -> [c], got " + names.get(JobStatus.FAILED));
        });
    }

    private static void scenario18_specParser() {
        run("Scenario 18: Spec parser reads id|name|priority|deps", () -> {
            JobSpecParser parser = new JobSpecParser();

            Job job = parser.parse("build|Build App|10|compile, lint");
            check(job.getId().equals("build"), "expected id 'build', got " + job.getId());
            check(job.getName().equals("Build App"), "expected name 'Build App', got " + job.getName());
            check(job.getPriority() == 10, "expected priority 10, got " + job.getPriority());
            check(job.getDependencies().equals(new HashSet<>(Arrays.asList("compile", "lint"))),
                    "expected deps {compile, lint}, got " + job.getDependencies());

            Job solo = parser.parse("solo|Solo|3|");
            check(solo.getDependencies().isEmpty(), "an empty dependency field means no dependencies, got " + solo.getDependencies());
        });
    }

    private static void scenario19_failureSkipsAllDescendants() {
        run("Scenario 19: A failed job causes all transitive dependents to be SKIPPED", () -> {
            Map<String, Job> jobs = jobs(job("a", 1), job("b", 1, "a"), job("c", 1, "b"));
            Scheduler scheduler = new Scheduler(jobs, new RetryPolicy(10, 1000, 0), new JobMetrics(), new EventBus());

            Map<String, JobTask> tasks = new HashMap<>();
            tasks.put("a", () -> {
                throw new RuntimeException("a fails");
            });
            tasks.put("b", () -> { });
            tasks.put("c", () -> { });
            scheduler.runAll(tasks);

            check(jobs.get("a").getStatus() == JobStatus.FAILED, "a should be FAILED, is " + jobs.get("a").getStatus());
            check(jobs.get("b").getStatus() == JobStatus.SKIPPED, "b should be SKIPPED, is " + jobs.get("b").getStatus());
            check(jobs.get("c").getStatus() == JobStatus.SKIPPED, "c (grandchild) should be SKIPPED, is " + jobs.get("c").getStatus());
        });
    }

    private static void scenario20_jobRunsOnlyAfterAllDependencies() {
        run("Scenario 20: A job with two dependencies waits for BOTH", () -> {
            Map<String, Job> jobs = jobs(job("a", 1), job("b", 10), job("c", 100, "a", "b"));
            Scheduler scheduler = new Scheduler(jobs, new RetryPolicy(10, 1000, 0), new JobMetrics(), new EventBus());

            Map<String, JobTask> tasks = new HashMap<>();
            for (String id : Arrays.asList("a", "b", "c")) {
                tasks.put(id, () -> { });
            }
            scheduler.runAll(tasks);

            check(scheduler.getExecutionOrder().equals(Arrays.asList("b", "a", "c")),
                    "expected order [b, a, c], got " + scheduler.getExecutionOrder());
        });
    }

    private static void scenario21_jobAttemptedRetriesPlusOneTimes() {
        run("Scenario 21: With maxRetries=2 an always-failing job is attempted 3 times", () -> {
            Map<String, Job> jobs = jobs(job("a", 1));
            Scheduler scheduler = new Scheduler(jobs, new RetryPolicy(10, 1000, 2), new JobMetrics(), new EventBus());

            Map<String, JobTask> tasks = new HashMap<>();
            tasks.put("a", () -> {
                throw new RuntimeException("always fails");
            });
            scheduler.runAll(tasks);

            check(jobs.get("a").getAttempts() == 3, "expected 3 attempts (1 + 2 retries), got " + jobs.get("a").getAttempts());
            check(jobs.get("a").getStatus() == JobStatus.FAILED, "expected FAILED, got " + jobs.get("a").getStatus());
        });
    }

    private static void scenario22_happyPathChain() {
        run("Scenario 22: A simple dependency chain runs in order and completes", () -> {
            Map<String, Job> jobs = jobs(job("a", 1), job("b", 1, "a"), job("c", 1, "b"));
            JobMetrics metrics = new JobMetrics();
            Scheduler scheduler = new Scheduler(jobs, new RetryPolicy(10, 1000, 1), metrics, new EventBus());

            Map<String, JobTask> tasks = new HashMap<>();
            for (String id : Arrays.asList("a", "b", "c")) {
                tasks.put(id, () -> { });
            }
            scheduler.runAll(tasks);

            check(scheduler.getExecutionOrder().equals(Arrays.asList("a", "b", "c")),
                    "expected order [a, b, c], got " + scheduler.getExecutionOrder());
            check(metrics.getCompleted() == 3, "expected 3 completed, got " + metrics.getCompleted());
            for (Job job : jobs.values()) {
                check(job.getStatus() == JobStatus.COMPLETED, job.getId() + " should be COMPLETED, is " + job.getStatus());
            }
        });
    }

    // ---- helpers ----

    private static Job job(String id, int priority, String... dependencies) {
        return new Job(id, id, priority, new HashSet<>(Arrays.asList(dependencies)));
    }

    private static Job job(String id, String name, int priority) {
        return new Job(id, name, priority, new HashSet<>());
    }

    private static Map<String, Job> jobs(Job... jobs) {
        Map<String, Job> map = new LinkedHashMap<>();
        for (Job job : jobs) {
            map.put(job.getId(), job);
        }
        return map;
    }

    private interface ScenarioBody {
        void run() throws Exception;
    }

    private static boolean scenarioFailedFlag = false;

    private static void run(String name, ScenarioBody body) {
        try {
            scenarioFailedFlag = false;
            body.run();
            if (scenarioFailedFlag) {
                failCount++;
                System.out.println("[FAIL] " + name);
            } else {
                passCount++;
                System.out.println("[PASS] " + name);
            }
        } catch (Exception e) {
            failCount++;
            System.out.println("[CRASHED] " + name + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void check(boolean condition, String failureMessage) {
        if (!condition) {
            scenarioFailedFlag = true;
            System.out.println("        assertion failed: " + failureMessage);
        }
    }
}
