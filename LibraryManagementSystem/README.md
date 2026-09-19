# Library Management System (Debugging Exercise)

A small layered Java service (model → repository → service) simulating a
library's book/member/loan management, built as a debugging exercise.

## Structure
```
PRD.md                     - the spec: what the system is supposed to do
BUG_CHECKLIST.md           - categories of bugs seeded in this project (no locations)
src/
  Main.java                - scenario runner; prints PASS/FAIL/CRASHED per scenario
  com/library/model/       - Book, Member, Loan
  com/library/repository/  - BookRepository, MemberRepository (in-memory storage)
  com/library/service/     - LibraryService (all business logic/rules)
```

## How to compile & run

From this directory:

```bash
javac -d out src/com/library/model/*.java src/com/library/repository/*.java src/com/library/service/*.java src/Main.java
java -cp out Main
```

Or, if you're on Windows PowerShell, the same two commands work as-is.

## What "done" looks like
`Main` runs 10 scenarios derived from the PRD's acceptance criteria. Right
now some show `[FAIL]` or `[CRASHED]` — that's expected. Your job is to
read `PRD.md`, understand what each scenario should do, find the bug(s) in
`LibraryService` / the model classes causing the mismatch, fix them, and
get all 10 scenarios to `[PASS]` without changing what the scenarios
assert (the scenarios encode the spec — the production code is what's
wrong, not the tests).

`BUG_CHECKLIST.md` lists the 6 categories of bugs seeded here, if you want
a hint about *what kind* of mistake to look for without being told where.
