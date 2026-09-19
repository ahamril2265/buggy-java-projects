# Product Requirements Document — Library Management System (LMS)

## 1. Overview
The Library Management System (LMS) is a backend service that lets a library
track its book inventory, manage member registrations, and handle the
borrow/return lifecycle of books, including fee calculation for late returns.

This is a simplified, in-memory version of the system (no database, no HTTP
layer) built to mirror a typical layered SDE service:

```
Main (demo/test runner)
   -> LibraryService        (business logic / rules)
       -> BookRepository    (in-memory storage for books)
       -> MemberRepository  (in-memory storage for members)
```

## 2. Actors
- **Librarian** — adds/removes books, registers members, views reports.
- **Member** — borrows and returns books.

## 3. Functional Requirements

### 3.1 Book Catalog
- FR-1: A book has an `isbn`, `title`, `author`, and `totalCopies`.
- FR-2: The system tracks how many copies are currently **available** vs.
  **on loan**. Available copies must never be negative and must never exceed
  `totalCopies`.
- FR-3: Librarians can search books by title (case-insensitive, partial
  match) or by exact ISBN.
- FR-4: Searching with a blank/empty query, or for a book that doesn't
  exist, must return an empty result — never throw an exception, never
  return `null`.

### 3.2 Membership
- FR-5: A member has a `memberId`, `name`, and a list of currently borrowed
  loans.
- FR-6: A member may have **at most 3 books checked out at the same time**
  (this is a hard limit — the 4th checkout attempt must be rejected).
- FR-7: A member cannot borrow two copies of the **same book** at the same
  time (no duplicate active loans for one ISBN per member).

### 3.3 Borrowing Rules
- FR-8: Standard loan period is **14 days** from the day the book is
  borrowed. If borrowed on day 0, it is due at the end of day 14 (day 14 is
  still on time; day 15 is late).
- FR-9: A book can only be borrowed if `availableCopies > 0`.
- FR-10: When a book is borrowed, `availableCopies` decreases by 1; when
  returned, it increases by 1.

### 3.4 Returning & Late Fees
- FR-11: When a member returns a book, the system calculates how many full
  days late the return is (0 if on time or early).
- FR-12: Late fee is **$0.50 per day late**. A book returned exactly on the
  due date owes **$0.00**.
- FR-13: Returning a book the member does not currently have on loan must be
  rejected cleanly (no crash, clear failure result).

### 3.5 Reporting
- FR-14: The librarian can retrieve a member's full loan history (past and
  current loans).
- FR-15: The librarian can retrieve the live list of a book's available
  copies count at any time. This value must reflect real-time state — a
  caller must not be able to corrupt the repository's internal state by
  modifying a returned report/collection.

## 4. Non-functional Requirements
- NFR-1: All public service methods must handle invalid input (nulls,
  unknown IDs, blank strings) gracefully — returning a failure/empty result
  rather than throwing unchecked exceptions for expected error conditions.
- NFR-2: Business rules (loan limit, due dates, fees) must be implemented
  as plain, readable logic — no external frameworks required for this
  version.

## 5. Out of Scope (for this version)
- Persistence (database/file storage)
- Authentication/authorization
- HTTP/REST API layer (a `Main` class simulates calls instead)
- Reservations/holds queue

## 6. Acceptance Criteria (sample scenarios)
1. Registering a member and borrowing an available book succeeds and
   decrements `availableCopies`.
2. Borrowing a book with 0 available copies is rejected.
3. A member attempting a 4th simultaneous loan is rejected.
4. A member cannot hold two active loans for the same ISBN.
5. Returning a book exactly 14 days after borrowing incurs **no fee**.
6. Returning a book 3 days late incurs a **$1.50** fee.
7. Searching with an empty string returns an empty list, not an error.
8. Modifying the list returned by a "list all books" report does not affect
   the actual catalog.

`Main.java` runs these scenarios and prints `PASS`/`FAIL` for each — a
`FAIL` means there's a bug to find in the corresponding service/repository
code.
