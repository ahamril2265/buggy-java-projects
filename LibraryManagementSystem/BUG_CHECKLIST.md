# Bug Checklist

There are **6 intentional bugs** in this codebase, one per category below.
Locations are not given — use `PRD.md` (the intended behavior) and the
output of `Main` (which scenarios PASS/FAIL/CRASH) to track each one down.

- [ ] **Off-by-one error** — something involving a day count is one off
      from what the PRD specifies.
- [ ] **Wrong comparison operator** — a boundary check uses the wrong
      operator (e.g. `>` where it should be `>=`, or similar), letting a
      case through that should be rejected.
- [ ] **Missing null check** — a code path assumes a lookup always
      succeeds and doesn't handle the "not found" case, causing a crash
      instead of a clean failure.
- [ ] **Wrong variable/method used** — a copy-paste style slip where the
      code calls/uses the wrong (but similarly-named) thing, silently
      producing incorrect state.
- [ ] **Incorrect calculation logic** — a numeric calculation is anchored
      to the wrong reference point, producing a plausible-looking but
      wrong number.
- [ ] **Broken encapsulation** — an internal collection is handed out
      directly, so a caller can mutate it and corrupt internal state
      without going through any service method.

## How to work through it
1. Read `PRD.md` fully first — the bugs only make sense relative to the
   spec.
2. Run the project (see `README.md`) and read the `[FAIL]` / `[CRASHED]`
   output from `Main` — each failing scenario points at one bug.
3. Fix one bug at a time, recompile, and re-run `Main` until all 10
   scenarios show `[PASS]`.
4. Don't just patch the symptom shown by the assertion — check nearby
   code for the same class of mistake (e.g. if you find one off-by-one,
   make sure a sibling method doesn't have the same issue).
