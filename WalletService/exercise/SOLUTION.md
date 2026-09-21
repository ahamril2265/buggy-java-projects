# Solution: the 24 bugs, what they were and why

All 141 tests pass on H2 and on real PostgreSQL after these fixes (`./mvnw test`,
`./mvnw test -Dtest.db=postgres`). Each entry: **where**, **what was wrong**, **the fix**, **the
concept** (section numbers refer to `../../LEARNING_GUIDE.md`).

Paths are relative to `src/main/java/com/example/wallet/`.

---

## Group 1: Concurrency and locking (guide §6, §7)

### 1. Stale entity loaded before its lock, `service/TransferService.java` (`transfer`)
- **Wrong:** the fee wallet was found with `findByOwnerIdAndCurrency(...).getId()`, which loads a whole
  `Wallet` entity into Hibernate's persistence context *before* the row is locked. The later
  `SELECT ... FOR UPDATE` returned fresh data, but Hibernate keeps the object it already has, so the
  transaction worked on a stale copy and failed with an optimistic-lock error (`409`).
- **Fix:** look up **only the id** (`select w.id ...`) so nothing entity-shaped is cached first.
  ```java
  UUID feeWalletId = wallets.findIdByOwnerIdAndCurrency(Wallet.SYSTEM_OWNER, currency)
          .orElseThrow(() -> new DomainException(ErrorCode.UNSUPPORTED_CURRENCY, "..."));
  ```
- **Concept:** first-level cache / persistence context (guide §7.6); never read-then-lock the same entity.

### 2. Lock ordering (deadlock), `TransferService.lockAll`
- **Wrong:** wallets were locked one at a time in the order the caller listed them. Transfer A->B locks
  A then B while B->A locks B then A: a textbook deadlock.
- **Fix:** one query, `lockAllByIdOrdered(ids)`, which locks all rows in ascending id order so every
  transaction acquires locks in the same global order.
- **Concept:** lock ordering; deadlock needs a cycle, a fixed order removes the cycle.

### 3. Deposit without a lock, `service/WalletService.java` (`deposit`)
- **Wrong:** `wallets.findById(...)` (no lock) for a read-modify-write of the balance.
- **Fix:** `lockUserWallet(walletId)` like `withdraw` does. Without it concurrent deposits collide on the
  `@Version` column (`409`) or, with no version, silently lose updates.
- **Concept:** lost update; optimistic vs pessimistic locking.

### 4. Refund read without a lock, `TransferService.refund`
- **Wrong:** `transfers.findById(id)` instead of `findByIdForUpdate(id)`. Two concurrent refunds both
  passed the "refundable amount" check on the same stale state.
- **Fix:** lock the transfer row first, then the wallets (always in the same order).
- **Concept:** check-then-act race (TOCTOU).

---

## Group 2: Ledger, money and time (guide §5, §9, §11.5)

### 5. Fee rounding, `service/FeePolicy.java`
- **Wrong:** `RoundingMode.DOWN` (truncation). 0.5% of 1,100 is 5.5 and must round half-up to 6.
- **Fix:** `RoundingMode.HALF_UP`.
- **Concept:** rounding modes; money is `long` minor units + `BigDecimal` for calculations.

### 6. Wrong quantity in a ledger line, `TransferService.transfer`
- **Wrong:** the `FEE` entry recorded `-amountMinor` instead of `-fee` (copy-paste of the line above).
  The sender's balance dropped by the fee but the ledger said something else, breaking
  "balance == sum of ledger entries".
- **Fix:** `ledger.record(source, ..., EntryType.FEE, -fee, ...)`.
- **Concept:** invariants; double-entry ledger; copy-paste bugs.

### 7. Wall clock instead of the injected clock, `WalletService.withdraw`
- **Wrong:** the withdrawal ledger line used `Instant.now()`. The daily limit reads those timestamps
  through the injected `Clock`, so time-travel in tests (and any clock skew) gave wrong windows.
- **Fix:** `clock.instant()` everywhere.
- **Concept:** dependency injection of time; testable code.

### 8. Limit boundary, `service/LimitPolicy.java`
- **Wrong:** `alreadyWithdrawn + amount >= limit` rejected a withdrawal that exactly reaches the limit.
- **Fix:** `>`. "Up to and including the limit" is allowed.
- **Concept:** boundary conditions (`>` vs `>=`).

### 9. Unchecked overflow, `domain/Wallet.java` (`credit`)
- **Wrong:** `balance + amount` wraps to a negative number past `Long.MAX_VALUE`.
- **Fix:** `Math.addExact`, catch `ArithmeticException`, throw `BALANCE_OVERFLOW`.
- **Concept:** integer overflow; fail loudly instead of corrupting data.

---

## Group 3: Refunds (guide §4)

### 10. Cap against the wrong quantity, `domain/Transfer.java` (`applyRefund`)
- **Wrong:** `refund > amountMinor` compares with the *original* amount, so two refunds could together
  exceed it.
- **Fix:** `refund > refundableMinor()` (amount minus what was already refunded).

### 11. Status never reaches REFUNDED, `Transfer.applyRefund`
- **Wrong:** `refundedMinor > amountMinor` is never true (the cap prevents it).
- **Fix:** `refundedMinor == amountMinor`.
- **Concept:** state machines; conditions that can never be true.

---

## Group 4: Idempotency (guide §8.4)

### 12. Incomplete request fingerprint, `service/IdempotentExecutor.java` (`hash`)
- **Wrong:** hashed `operation + request.getClass().getName()`, i.e. only the *type* of the request,
  not its contents. A retry with a different amount or wallet looked identical.
- **Fix:** hash `operation + "\n" + mapper.writeValueAsString(request)` (the content).

### 13. Replay changes the status, `IdempotentExecutor.replay`
- **Wrong:** replays returned a hard-coded `200`.
- **Fix:** return the stored `record.getResponseStatus()` (`201`), so a replay is indistinguishable
  from the original response apart from the `Idempotent-Replayed` header.
- **Concept:** idempotency keys; safe retries.

---

## Group 5: State, validation and guards (guide §7, §8)

### 14. `readOnly` transaction around a write, `WalletService.changeStatus`
- **Wrong:** `@Transactional(readOnly = true)`. Hibernate does not flush changes in a read-only
  transaction, so freeze/close appeared to work but was never persisted (the biggest cascade: ~10
  tests).
- **Fix:** plain `@Transactional`.
- **Concept:** `@Transactional` semantics; flush; dirty checking.

### 15. Missing `@Positive`, `web/dto/MoneyRequest.java`
- **Wrong:** amount validation removed, so `0` / negative amounts reached the domain, which threw an
  `IllegalArgumentException`, and the catch-all handler turned that into `500`.
- **Fix:** `@Positive long amountMinor`. Validate at the boundary and return `400`.

### 16. Missing currency check on withdraw, `WalletService.withdraw`
- **Fix:** call `requireCurrency(wallet, currency)`, exactly like `deposit`.
- **Concept:** symmetric code paths must have symmetric checks.

### 17. Missing same-wallet guard, `TransferService.transfer`
- **Wrong:** the database constraint `ck_transfer_distinct_wallets` was the only protection, so the
  client got `409 CONSTRAINT_VIOLATION` instead of `422 SAME_WALLET_TRANSFER`.
- **Fix:** check `sourceId.equals(targetId)` first.
- **Concept:** validate in the domain; the DB is a safety net, not the user interface.

### 18. Missing system-wallet guard, `WalletService.lockUserWallet` (and `deposit` bypassing it)
- **Fix:** reject `wallet.isSystemWallet()` in `lockUserWallet` and make `deposit` use it.

### 19. Removed pre-check, `WalletService.createWallet`
- **Wrong:** without the existence check, the unique constraint fires and the client sees a generic
  constraint error instead of `WALLET_ALREADY_EXISTS`.
- **Fix:** restore the pre-check (the constraint stays as the race-proof backstop).

---

## Group 6: API surface and operations

### 20. Sort direction, `WalletService.transactions`: `ASC` -> `DESC` (newest first).
### 21. Missing upper bound, `WalletController`: restore `@Max(100)` on `size`.
### 22. Integer division, `web/dto/PageResponse.java`: `totalElements / size` truncates
(25 items at size 10 gave 2 pages). Use `page.getTotalPages()`.

### 23. MDC leak, `web/CorrelationIdFilter.java`
- **Wrong:** `MDC.put` without a `finally { MDC.remove }`. Servlet threads are reused, so the previous
  request's correlation id could appear in the next request's logs.
- **Fix:** wrap `chain.doFilter` in `try/finally`.
- **Concept:** thread-local state and thread pools (guide §6.7).

### 24. Config, `src/main/resources/application.yml`
- **Wrong:** `management.endpoints.web.exposure.include` lacked `prometheus`, so `/actuator/prometheus`
  returned `404`.
- **Fix:** `include: health,info,metrics,prometheus`.
- **Concept:** operational endpoints are configuration, and configuration can be wrong too.

---

## Why the bugs were "layered"

Several bugs hide each other: #1 (stale entity) made every concurrent transfer fail, hiding #2
(deadlock); #8 (limit `>=`) failed the daily-limit tests before #7 (wall-clock timestamp) could show;
#10 (refund cap) hid #4 (unlocked refund). Real systems behave the same way: after each fix, re-run
everything and expect the next problem to appear.

## What was in your own attempt
Your attempt is preserved in `../exercise-my-attempt/`. Things you did well: replacing the per-row
locking loop with one ordered query (`lockAll`), and adding jitter to the retry backoff (kept in the
solution). Two things to revisit: an extra `WALLET_NOT_ACTIVE` loop in `lockAll` duplicated a rule that
`Wallet.credit/debit` already enforce, and catching `ObjectOptimisticLockingFailureException` in the
idempotency retry loop turns a real bug (stale data) into a retry, which hides it.
