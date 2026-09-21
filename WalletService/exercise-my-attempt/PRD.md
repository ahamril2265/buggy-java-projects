# Product Requirements Document — Wallet Service

## 1. Overview
A wallet and payments backend. Users hold wallets in a currency; money moves through deposits,
withdrawals, fee-bearing transfers and refunds. Every balance change is recorded in a double-entry
ledger. The service is an HTTP JSON API backed by a relational database.

Stack: Java 21, Spring Boot 4, Spring Data JPA (Hibernate 7), Flyway, H2 (dev/test) / PostgreSQL
(production), Micrometer + Prometheus, JUnit 5 + MockMvc.

The 141 tests under `src/test` are the executable specification. A test that fails means the code
disagrees with this document.

## 2. Concepts
- **Money** is an integer in *minor units* (cents). There are no floating-point amounts anywhere.
- **Wallet:** `id`, `ownerId`, `currency`, `balanceMinor`, `status` (`ACTIVE`, `FROZEN`, `CLOSED`).
  One wallet per owner per currency. The owner id `SYSTEM_FEES` is reserved.
- **System fee wallets:** one per supported currency (`USD`, `EUR`, `GBP`), created by migration.
  They collect transfer fees and can never be used directly through the API.
- **Ledger entry:** an immutable, signed line (`+` credits, `-` debits) with the wallet's balance
  after the change. All lines of one business operation share a `transactionId`.
- **Transfer:** moves money between two wallets of the same currency; can be refunded.

## 3. API

Money-moving `POST` requests require an `Idempotency-Key` header (1-64 characters).

| Method & path | Body | Success |
|---|---|---|
| `POST /api/v1/wallets` | `{ownerId, currency}` | `201` + `Location` |
| `GET /api/v1/wallets/{id}` | | `200` |
| `PATCH /api/v1/wallets/{id}/status` | `{status}` | `200` |
| `POST /api/v1/wallets/{id}/deposits` | `{amountMinor, currency}` | `201` |
| `POST /api/v1/wallets/{id}/withdrawals` | `{amountMinor, currency}` | `201` |
| `GET /api/v1/wallets/{id}/transactions?page&size` | | `200` |
| `POST /api/v1/transfers` | `{sourceWalletId, targetWalletId, amountMinor, currency}` | `201` |
| `GET /api/v1/transfers/{id}` | | `200` |
| `POST /api/v1/transfers/{id}/refunds` | `{amountMinor}` | `201` |

Errors are RFC 9457 `application/problem+json` documents with a stable `code` and the request's
`correlationId`: `400` invalid request, `404` not found, `409` conflict, `422` business rule.

## 4. Functional requirements

### 4.1 Wallets and status
- FR-1: An owner has at most one wallet per currency (`409 WALLET_ALREADY_EXISTS`). Only supported
  currencies are allowed (`422 UNSUPPORTED_CURRENCY`); the reserved owner is rejected.
- FR-2: Status transitions: `ACTIVE <-> FROZEN`, and either to `CLOSED`. `CLOSED` is terminal
  (`409 INVALID_STATUS_TRANSITION`). Closing needs a zero balance (`422 WALLET_NOT_EMPTY`).
  Setting the current status again is a no-op. System wallets cannot be changed.
- FR-3: Frozen and closed wallets reject both credits and debits (`422 WALLET_NOT_ACTIVE`). Status
  changes are persisted.

### 4.2 Deposits and withdrawals
- FR-4: The request currency must equal the wallet's (`422 CURRENCY_MISMATCH`), for deposits *and*
  withdrawals. Amounts must be positive (`400`); no request may ever *add* money by withdrawing a
  negative amount. A wallet balance can never go below zero (`422 INSUFFICIENT_FUNDS`).
- FR-5: Per-transaction maximum is 1,000,000 minor units (`422 AMOUNT_LIMIT_EXCEEDED`).
- FR-6: **Daily withdrawal limit** of 500,000 minor units in any **rolling 24 hours**, per wallet.
  Withdrawing exactly up to the limit is allowed; one more unit is rejected
  (`422 DAILY_LIMIT_EXCEEDED`). A withdrawal made exactly 24 hours ago no longer counts. Only
  withdrawals count. All time comes from the injected `Clock`.
- FR-7: A wallet's balance can never overflow `long`; crediting past the maximum is rejected
  (`422 BALANCE_OVERFLOW`) rather than wrapping around.
- FR-8: System wallets cannot be deposited into, withdrawn from, or otherwise used directly
  (`422 SYSTEM_WALLET_NOT_ALLOWED`).

### 4.3 Transfers and fees
- FR-9: The sender pays `amount + fee`; the target receives `amount`; the fee is credited to the
  system wallet of the currency. Fee = 0.5% of the amount (50 basis points) **rounded half-up**,
  minimum 1 minor unit.
- FR-10: Source and target must differ (`422 SAME_WALLET_TRANSFER`), be user wallets, exist, and use
  the request currency. A failed transfer changes nothing: no balances, no ledger lines, no
  transfer record.
- FR-11: **Ledger integrity:** the entries of every transfer sum to zero, and every wallet's balance
  equals the sum of its ledger entries at all times. Each ledger line's `balanceAfter` is the
  wallet's running balance at that point.

### 4.4 Refunds
- FR-12: A refund moves money from the target back to the source. The **cumulative** refunded amount
  can never exceed the transfer amount (`422 REFUND_EXCEEDS_TRANSFER`); the fee is not refunded.
  The receiver must still hold the funds. Status: `COMPLETED` -> `PARTIALLY_REFUNDED` ->
  `REFUNDED` (reached when the refunded total equals the transfer amount exactly).
- FR-13: Concurrent refunds of one transfer can never refund more than the transfer amount in total.

### 4.5 Idempotency
- FR-14: Repeating a request with the same `Idempotency-Key` and the same content returns the
  original response with the **original status code** plus an `Idempotent-Replayed: true` header,
  and performs the operation **once**. This holds under concurrency.
- FR-15: Reusing a key with a *different* request (any different field, different path resource,
  or different operation) is rejected (`422 IDEMPOTENCY_KEY_REUSED`). Failed requests are not cached.

### 4.6 Concurrency
- FR-16: Concurrent operations never lose updates, overdraw a wallet, deadlock, or leave the ledger
  inconsistent. Concurrent deposits to one wallet all succeed and all count.

### 4.7 Reporting and API behaviour
- FR-17: `GET .../transactions` returns ledger lines **newest first**, with `page` (>= 0, default 0),
  `size` (1-100, default 20), `totalElements` and `totalPages` (the number of pages needed to hold
  all elements). Out-of-range paging parameters are `400`.
- FR-18: Every response carries `X-Correlation-Id` (the caller's, if it is a safe token, otherwise a
  generated one). The id is present in error bodies, and never leaks from one request into the
  next.
- FR-19: `/actuator/health`, its liveness/readiness probes, and `/actuator/prometheus` are exposed.
  A `wallet_transfers_total` counter is published.
- FR-20: A malformed body, invalid field or unparsable id is a `400`, never a `500`. Error bodies
  never contain stack traces or internals.

## 5. Out of scope
Authentication, FX conversion, event publishing, rate limiting, soft deletes.
