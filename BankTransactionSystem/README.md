# Bank Account & Transaction System (Debugging Exercise — Hard Mode)

A layered Java service (model → repository → service) simulating account
opening, deposits/withdrawals with overdraft rules, transfers, monthly
interest, and reporting. This is the harder follow-up to the Library
Management System exercise — 9 seeded bugs instead of 6, and some of them
interact with each other.

## Structure
```
PRD.md                     - the spec: exact rules for overdraft, interest, transfers, etc.
BUG_CHECKLIST.md           - categories of bugs seeded in this project (no locations)
src/
  Main.java                - scenario runner; prints PASS/FAIL/CRASHED per scenario
  com/bank/model/          - Account, Transaction, and the AccountType/AccountStatus/TransactionType enums
  com/bank/repository/     - AccountRepository (in-memory storage)
  com/bank/service/        - BankService (all business logic/rules)
```

## How to compile & run

From this directory:

```bash
javac -d out src/com/bank/model/*.java src/com/bank/repository/*.java src/com/bank/service/*.java src/Main.java
java -cp out Main
```

## What "done" looks like
`Main` runs 15 scenarios derived from the PRD's acceptance criteria.
Several currently show `[FAIL]`. Your job is to read `PRD.md`, understand
what each scenario should do, find the bug(s) in `BankService` / the model
classes causing the mismatch, fix them, and get all 15 scenarios to
`[PASS]` without changing `Main.java`'s assertions.

`BUG_CHECKLIST.md` lists the 9 categories of bugs seeded here if you want
a hint about *what kind* of mistake to look for without being told where.

Fair warning versus last time: a couple of these bugs are more subtle than
last round (wrong operator precedence, a rounding rule, a state check
that's incomplete rather than missing outright), and one bug can make an
otherwise-unrelated scenario behave oddly if you fix things in the wrong
order — read the assertion messages carefully rather than assuming the
first thing you spot is the whole story.
