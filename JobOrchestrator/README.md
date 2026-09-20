# Job Orchestration Engine (Debugging Exercise — Level 4)

A workflow engine: dependency ordering, priority queue, retries with
exponential backoff, an LRU cache, a rate limiter, an event bus, cost
accounting, metrics, a spec parser, a daily-schedule calculator and a
scheduler that ties them together. 18 seeded bugs — mostly Java
language/library pitfalls (overflow, `==` on boxed values, `BigDecimal`,
regex `split`, DST arithmetic, a real thread race, `TreeSet` contracts).

## Structure
```
PRD.md               - the spec
BUG_CHECKLIST.md     - bug categories (no locations)
src/Main.java        - 22 scenarios, PASS/FAIL/CRASHED
src/com/orchestrator/model/    - Job, JobStatus, JobEvent
src/com/orchestrator/service/  - Scheduler, DependencyResolver, PriorityJobQueue,
                                 RetryPolicy, ResultCache, RateLimiter, JobRegistry,
                                 CostCalculator, JobMetrics, EventBus, JobReport,
                                 DailySchedule, JobSpecParser, JobTask
```

## Build & run
Requires Java 17+.

```bash
javac -d out src/com/orchestrator/model/*.java src/com/orchestrator/service/*.java src/Main.java
java -cp out Main
```

## Goal
All 22 scenarios `[PASS]`, without editing the assertions in `Main.java`.
Currently 18 fail/crash and 4 baseline scenarios pass.
