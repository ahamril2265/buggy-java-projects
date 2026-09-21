# Bug Checklist

There are **24 intentional bugs** in this codebase, in the production code only. The 141 tests in
`src/test` are correct and must not be edited. No locations are given.

Right now **59 tests fail**. Several bugs share a symptom, and a few are **layered**: they only
become visible once another bug that hides them is fixed. Re-run the suite after every fix.

- [ ] **Wrong rounding mode** on a money calculation.
- [ ] **Copy-paste slip**: a ledger line records a different quantity than the one it describes.
- [ ] **Boundary operator** on a limit check (`>` vs `>=`).
- [ ] **Time source**: one code path reads the wall clock instead of the injected `Clock`.
- [ ] **Cap compared against the wrong quantity**: a cumulative limit checked against a per-request
      value.
- [ ] **Terminal-state condition** that can never be reached (or is reached one step too late).
- [ ] **Check-then-act without a lock**: a read that should be exclusive is not.
- [ ] **Incomplete request fingerprint**: the idempotency comparison ignores part of the request.
- [ ] **Replay fidelity**: a replayed response differs from the original.
- [ ] **Lock ordering**: multiple rows locked in caller-dependent order, so opposing requests can
      deadlock.
- [ ] **Stale persistence context**: an entity is loaded before its row is locked, so the locked
      re-read is ignored.
- [ ] **Missing lock on a write path**: a read-modify-write that relies only on optimistic
      versioning.
- [ ] **Validation removed**: an input constraint that guards against a serious misuse.
- [ ] **Missing guard on one of two symmetric paths** (a check exists on deposit, but not on
      withdrawal).
- [ ] **Sort direction** of a paged listing.
- [ ] **Unbounded input**: a limit on a query parameter is missing.
- [ ] **Read-only transaction** used around a write, so the change is silently not flushed.
- [ ] **Missing domain check**: the database's constraint catches it instead, and the client sees the
      wrong error.
- [ ] **Missing authorization-style guard**: internal resources reachable through public endpoints.
- [ ] **Unchecked arithmetic**: a value can silently wrap around.
- [ ] **Context leak**: per-request logging context is never cleared.
- [ ] **Pre-check removed**: a friendly business error is replaced by a raw constraint violation.
- [ ] **Configuration**: an operational endpoint is not exposed.
- [ ] **Integer division** in derived pagination metadata.

## How to approach it
1. Read `PRD.md`; the tests reference its requirement wording.
2. `./mvnw test` (Windows: `mvnw.cmd test`). Start with the failures that print a clear expected vs.
   actual (`expected:<201> but was:<200>`).
3. Focus with `-Dtest=WalletApiTest`, `-Dtest=ConcurrencyTest#transfersInOppositeDirectionsDoNotDeadlock`,
   etc. The concurrency tests print the bodies of unexpected `409`/`500` responses; read them.
4. Many failures are one root cause. Fix the cause, not each symptom, then re-run.
5. When the H2 run is green, run it against real PostgreSQL too: `./mvnw test -Dtest.db=postgres`.
   Race conditions can hide on one database and not the other. Run `ConcurrencyTest` several times.
