# Wallet Service (reference implementation)

A wallet and payments backend: wallets, a double-entry ledger, deposits, withdrawals, fee-bearing
transfers, partial refunds, idempotent requests, daily limits, and operational endpoints.

**Stack:** Java 21, Spring Boot 4.1, Spring Data JPA (Hibernate 7), Flyway, PostgreSQL (H2 for
dev/test), Micrometer/Prometheus, JUnit 5 + MockMvc. Built with Maven (wrapper included).

> This is the **clean, fully-tested reference**. The sibling `exercise/` project is a copy of this
> code with bugs injected for debugging practice. Don't read this one first if you want the
> exercise to be a real challenge.

## Running it

```bash
./mvnw test                      # 141 tests on in-memory H2
./mvnw test -Dtest.db=postgres   # the same 141 tests on a real (embedded) PostgreSQL, no Docker needed
./mvnw spring-boot:run           # starts on :8080 with in-memory H2
docker compose up --build        # app + PostgreSQL (prod profile)
```

Probes: `/actuator/health/liveness`, `/actuator/health/readiness`. Metrics: `/actuator/prometheus`.

## API

All money is an integer in **minor units** (cents). Money-moving `POST`s require an `Idempotency-Key`
header (1-64 chars).

| Method & path | Purpose |
|---|---|
| `POST /api/v1/wallets` | Create a wallet `{ownerId, currency}` (one per owner per currency) |
| `GET /api/v1/wallets/{id}` | Read a wallet |
| `PATCH /api/v1/wallets/{id}/status` | `ACTIVE` / `FROZEN` / `CLOSED` |
| `POST /api/v1/wallets/{id}/deposits` | `{amountMinor, currency}` |
| `POST /api/v1/wallets/{id}/withdrawals` | `{amountMinor, currency}` |
| `GET /api/v1/wallets/{id}/transactions?page&size` | Ledger lines, newest first (size 1-100) |
| `POST /api/v1/transfers` | `{sourceWalletId, targetWalletId, amountMinor, currency}` |
| `GET /api/v1/transfers/{id}` | Read a transfer |
| `POST /api/v1/transfers/{id}/refunds` | Partial or full refund `{amountMinor}` |

Errors are RFC 9457 `application/problem+json` with a stable `code` and the request's
`correlationId`. `404` not found, `409` conflict, `422` business-rule violation, `400` invalid request.

## Business rules

- **Fees:** a transfer costs the sender 0.5% of the amount (basis points, rounded half-up, minimum
  1 minor unit), on top of the amount. The fee goes to a per-currency system wallet. Fees are not
  refunded.
- **Limits:** per-transaction maximum 10,000.00; withdrawals limited to 5,000.00 in any rolling 24
  hours (an entry exactly 24h old has expired; withdrawing exactly up to the limit is allowed).
- **Wallet status:** `ACTIVE` <-> `FROZEN` -> `CLOSED`. `CLOSED` is terminal and needs a zero
  balance. Frozen and closed wallets reject credits and debits.
- **Refunds:** cumulative refunds can never exceed the transfer amount; the receiver must still have
  the funds.
- **Currency:** wallets and requests must agree on currency; only configured currencies are allowed.

## Design decisions

- **Ledger, not just balances.** Every balance change writes signed `ledger_entries`. For any
  transfer the entries sum to zero, and a wallet's balance always equals the sum of its entries.
- **Pessimistic locking with a fixed order.** Multi-wallet operations lock rows through one method
  that orders by id, so opposing transfers cannot deadlock. `@Version` is a second line of defence.
- **Never load an entity before locking it.** Loading a wallet through an ordinary query and then
  locking it returns the stale managed copy. The fee wallet is therefore resolved by *id only* first.
- **Idempotency in the same transaction.** The key is inserted (reserved) inside the transaction that
  moves the money. A concurrent duplicate blocks on the primary key, then replays the stored
  response. Failed requests are rolled back, so failures are never cached.
- **All time from an injected `Clock`,** so time-window rules are testable without sleeping.
- **Database-level safety nets:** check constraints (non-negative balance, refund bounds, positive
  amounts) back up the application rules.

## Deliberately out of scope

Authentication/authorization (put it behind a gateway), multi-currency FX, event publishing / outbox,
rate limiting, and soft-deleting wallets.
