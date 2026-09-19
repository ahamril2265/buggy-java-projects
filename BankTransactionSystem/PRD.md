# Product Requirements Document — Bank Account & Transaction System

## 1. Overview
A backend service simulating core retail-banking operations: opening
accounts, deposits/withdrawals, transfers between accounts, monthly
interest for savings accounts, and statement/history reporting.

Layered like a real service, no framework, no persistence:

```
Main (demo/test runner)
   -> BankService          (all business rules)
       -> AccountRepository (in-memory storage)
```

## 2. Domain Model
- **Account**: `id`, `ownerName`, `type` (`CHECKING` or `SAVINGS`),
  `status` (`ACTIVE`, `FROZEN`, or `CLOSED`), `balance`, and a list of
  `Transaction`s.
- **Transaction**: `id`, `accountId`, `type` (`DEPOSIT`, `WITHDRAWAL`,
  `TRANSFER_IN`, `TRANSFER_OUT`, `INTEREST`), `amount`, `timestamp`
  (a date), `balanceAfter`.

## 3. Functional Requirements

### 3.1 Account Lifecycle
- FR-1: An account is opened with an initial deposit, which is itself
  recorded as the account's first transaction.
- FR-2: An account's transaction history is **private to that account** —
  no two accounts ever share the same underlying transaction list, even
  internally.
- FR-3: Accounts can be `FROZEN` (temporarily blocks deposits and
  withdrawals) and later returned to `ACTIVE`, or permanently `CLOSED`.
  `CLOSED` is terminal.

### 3.2 Deposits
- FR-4: A deposit succeeds only if the account exists, the amount is
  positive, and the account is **not** `FROZEN` and **not** `CLOSED`.
  There is no exception for small amounts — a closed account rejects
  *every* deposit, regardless of size.

### 3.3 Withdrawals & Overdraft
- FR-5: `SAVINGS` accounts may never go below a balance of `$0`.
- FR-6: `CHECKING` accounts have overdraft protection: the balance may go
  as low as **-$500, inclusive**. A withdrawal is only rejected if it
  would push the balance to *less than* -$500 (i.e. exactly -$500 is a
  valid, allowed balance).
- FR-7: `FROZEN` or `CLOSED` accounts reject **all** withdrawal attempts,
  for **any** amount — there is no dollar threshold under which a
  frozen/closed account is allowed to release funds.

### 3.4 Transfers
- FR-8: A transfer from account A to account B is **atomic**: if the
  withdrawal from A succeeds but the deposit into B fails for any reason
  (e.g. B is frozen), the withdrawal from A **must be rolled back** so
  that A's balance and transaction history end up exactly as if the
  transfer never happened. Money must never simply vanish.

### 3.5 Interest
- FR-9: Once a month, interest is applied to all `SAVINGS` accounts at a
  rate of **0.5%** of the current balance.
- FR-10: Interest amounts must be rounded to the nearest cent using
  standard rounding (round-half-up), not truncated toward zero.
- FR-11: Applying interest is a batch job over a list of accounts. If an
  individual account cannot receive interest (e.g. it has been closed
  since the job started), that failure must be **recorded and reported**
  back to the caller as an error for that account — it must never be
  silently discarded. Other accounts in the batch must still be
  processed normally.

### 3.6 Reporting
- FR-12: `getStatement(accountId, from, to)` returns every transaction
  whose date falls **within the inclusive range** `[from, to]` — a
  transaction dated exactly `from` or exactly `to` must be included.
- FR-13: `getTransactionHistorySorted(accountId)` returns the account's
  transactions ordered by **timestamp, most recent first**. Ordering is
  always by date — never by amount or any other field.

## 4. Non-functional Requirements
- NFR-1: Business-rule violations return a failure result; they never
  throw unchecked exceptions for expected conditions.
- NFR-2: Unexpected errors during a batch operation must be surfaced to
  the caller, never swallowed.

## 5. Out of Scope
- Persistence, authentication, concurrency/threading, an HTTP layer,
  multi-currency support.

## 6. Acceptance Criteria (sample scenarios)
`Main.java` runs these as PASS/FAIL/CRASHED scenarios:

1. Opening an account records the initial deposit as a transaction.
2. Depositing into an `ACTIVE` account succeeds.
3. Depositing into a `FROZEN` account is rejected.
4. Depositing into a `CLOSED` account is rejected, even for $1.
5. A `CHECKING` withdrawal that leaves the balance at exactly -$500
   succeeds.
6. A `CHECKING` withdrawal that would leave the balance below -$500 is
   rejected.
7. A `SAVINGS` withdrawal that would leave the balance below $0 is
   rejected.
8. A small withdrawal attempt on a `CLOSED` account is rejected.
9. A large withdrawal attempt on a `CLOSED` account is rejected.
10. A transfer to a `FROZEN` target account fails, and the source
    account's balance/history is unaffected (rolled back).
11. A monthly interest batch over one `ACTIVE` savings account and one
    `CLOSED` savings account applies interest to the active one and
    reports exactly one error (for the closed one).
12. Interest of 0.5% on a balance chosen so the raw interest is a
    half-cent value rounds to the nearest cent correctly (not truncated).
13. `getTransactionHistorySorted` returns transactions ordered by date,
    most recent first, regardless of transaction amounts.
14. `getStatement` includes transactions dated exactly on the `from` and
    `to` boundary dates.
15. Two independently opened accounts never share transaction history —
    depositing into one never makes the transaction appear in the
    other's history.
