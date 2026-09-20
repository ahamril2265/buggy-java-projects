# Product Requirements Document — Job Orchestration Engine

## 1. Overview
A small workflow engine: jobs with priorities and dependencies are
validated, ordered, executed with retries and exponential backoff, and
reported on. It also includes supporting infrastructure — a result cache,
a rate limiter, an event bus, cost accounting, metrics, a spec parser and
a daily-schedule calculator.

No frameworks, no persistence. Java 17+.

```
Main (scenario runner)
   -> Scheduler            (readiness, execution, retries, failure propagation)
   -> DependencyResolver   (standalone: topological ordering + cycle detection)
   -> PriorityJobQueue, RetryPolicy, ResultCache, RateLimiter, JobRegistry,
      CostCalculator, JobMetrics, EventBus, JobReport, DailySchedule, JobSpecParser
      (standalone components; the Scheduler uses RetryPolicy, JobMetrics, EventBus)
   -> model: Job, JobStatus, JobEvent
```

This is the hardest exercise so far. Most bugs are **language/library
pitfalls** (numeric overflow, reference vs value equality, regex
metacharacters, time-zone arithmetic, thread safety, collection
contracts) rather than plain business-rule mistakes.

## 2. Domain Model
- `Job`: `id`, `name`, `priority` (any `int`; higher runs first),
  `dependencies` (ids of jobs that must complete first), a `JobStatus`, an
  attempt counter.
- `JobStatus`: `PENDING`, `RUNNING`, `RETRYING`, `COMPLETED`, `FAILED`,
  `SKIPPED`, `CANCELLED`.

## 3. Functional Requirements

### 3.1 Jobs
- FR-1: A job's dependency set is **its own**. Mutating the collection
  that was passed to the constructor afterwards must not change the job.
- FR-2: Two jobs have the same priority iff their priority *values* are
  equal — for every `int` value, not only small ones.
- FR-3: `Job` natural ordering is priority **descending**, ties broken by
  `id`, and is consistent with `equals` (only the same job compares as 0).

### 3.2 Dependency resolution
- FR-4: `resolveOrder` returns job ids such that every job appears after
  all of its dependencies. Shared dependencies (diamond shapes:
  a→b, a→c, b→d, c→d) are valid and must not be reported as cycles.
- FR-5: A genuine dependency cycle throws `IllegalStateException`. A
  dependency on an unknown job id throws `IllegalArgumentException`.

### 3.3 Queue, retries, backoff
- FR-6: `PriorityJobQueue` polls the highest priority first, for **all**
  `int` priorities (including `Integer.MAX_VALUE` and negatives). Equal
  priorities are served first-in-first-out.
- FR-7: With `maxRetries = N`, a job is attempted at most **N + 1** times
  (1 initial attempt + N retries).
- FR-8: Backoff delay before retry number `r` (1-based) is
  `baseDelayMs * 2^(r-1)`, capped at `maxDelayMs`. The cap applies for any
  retry number, however large.

### 3.4 Scheduler
- FR-9: A job is **ready** when it is `PENDING` and **all** of its
  dependencies are `COMPLETED` (a job with no dependencies is ready).
- FR-10: Among ready jobs the highest priority runs first (ties by id).
- FR-11: When a job ends `FAILED`, **every** job that transitively depends
  on it (children, grandchildren, ...) that is still `PENDING` becomes
  `SKIPPED`.

### 3.5 Infrastructure
- FR-12: `ResultCache` is a true **LRU** cache: reading or writing an
  entry makes it most-recently-used; when over capacity the
  *least-recently-used* entry is evicted.
- FR-13: `RateLimiter(maxEvents, windowMs)` allows at most `maxEvents`
  acquisitions in any sliding window. An event that is **exactly**
  `windowMs` old has expired and no longer counts.
- FR-14: `JobRegistry.findByName` compares names by **value**
  (`equals`), regardless of how the lookup string was created.
- FR-15: `JobMetrics` counters are accurate under concurrent use — no lost
  updates when many threads record results at the same time.
- FR-16: `EventBus.publish` delivers an event to **every** listener. If a
  listener throws, the failure is recorded (one message per failure in the
  returned list) and the remaining listeners still run.

### 3.6 Money, reports, parsing, time
- FR-17: `jobCost(ratePerSecond, seconds)` is exact decimal arithmetic on
  the rate *as written* (e.g. `1.005`), rounded `HALF_UP` to 2 decimals —
  so `1.005 * 1s = 1.01`.
- FR-18: `JobReport.sortedByPriority` returns **every** job (none dropped)
  in `Job` natural order. `namesByStatus` returns, per status, the sorted
  list of job names — several jobs may share a status.
- FR-19: `JobSpecParser` parses `id|name|priority|dep1,dep2`. An empty
  dependency field means no dependencies; whitespace around dependency
  names is ignored.
- FR-20: `DailySchedule.nextRun(last)` returns the same **local
  wall-clock time** on the next calendar day, including across
  daylight-saving transitions (a 09:00 job stays at 09:00).

## 4. Non-functional Requirements
- NFR-1: Expected failures are reported through statuses/results; one
  failing listener or job never prevents unrelated work from running.
- NFR-2: No silent data loss (dropped jobs, dropped events, lost counts).

## 5. Out of Scope
Persistence, real sleeping/backoff waits (delays are computed and
recorded, not slept), distributed execution.

## 6. Acceptance Criteria
See the scenario names in `Main.java` (22 scenarios, one or two per
requirement above, plus a few baseline scenarios that should already
pass).
