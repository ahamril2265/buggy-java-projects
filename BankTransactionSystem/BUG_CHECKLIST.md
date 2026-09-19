# Bug Checklist

There are **9 intentional bugs** in this codebase — harder and more varied
than the previous exercise. One per category below. No locations given —
use `PRD.md` plus the `[FAIL]`/`[CRASHED]` output from `Main` to hunt each
one down.

- [ ] **Boundary/off-by-one error** — a limit check uses the wrong boundary,
      rejecting a value that should be exactly at the edge of what's
      allowed.
- [ ] **Wrong operator precedence** — a boolean expression combining `&&`
      and `||` without parentheses doesn't group the way the author
      intended, letting an edge case slip through.
- [ ] **Floating-point rounding error** — a monetary calculation truncates
      toward zero instead of rounding to the nearest cent, silently
      shortchanging by a cent in some cases.
- [ ] **Date-range boundary error** — a range filter excludes values that
      land exactly on the start or end of the range, when it should
      include them.
- [ ] **Wrong field used in a comparator** — a sort is supposed to order by
      one field but is actually comparing a different one, so the results
      come out in a misleading order.
- [ ] **Missing state-machine check** — an operation checks for one invalid
      account state but forgets to also check for another, letting an
      action through that should be blocked.
- [ ] **Non-atomic multi-step operation** — a two-step operation (where the
      first step can succeed and the second can fail) doesn't undo the
      first step when the second one fails, so state ends up inconsistent
      (in this case: money disappearing).
- [ ] **Silently swallowed exception** — a `catch` block catches an error
      and does nothing with it (no logging, no reporting to the caller),
      hiding a real failure.
- [ ] **Unintentionally shared mutable state** — an object that should have
      its own independent internal collection ends up sharing the same
      underlying collection instance with other objects, so changes to
      one leak into the other.

## How to work through it
1. Read `PRD.md` in full — several of these bugs only look wrong once you
   know the exact rule (e.g. the overdraft boundary, or the interest
   rounding rule).
2. Run `Main` and read every `[FAIL]`/`[CRASHED]` line and its assertion
   message — they tell you the expected vs. actual outcome.
3. Fix one bug at a time, recompile, and rerun until all 15 scenarios show
   `[PASS]`. Don't edit `Main.java`'s assertions — they encode the spec.
4. A couple of these bugs are subtle enough that fixing the *obvious*
   symptom won't be enough — trace back to the real root cause (e.g. one
   bug is about *where* a value comes from, not the arithmetic on it).
