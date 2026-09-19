# Bug Checklist

There are **14 intentional bugs** in this codebase — the hardest of the
three exercises so far. They span inheritance/polymorphism pitfalls, a
recursive algorithm, and collections/equals-hashCode gotchas, in addition
to the business-rule-style bugs from the previous two rounds. No
locations given — use `PRD.md` plus the `[FAIL]`/`[CRASHED]` output from
`Main` to hunt each one down.

- [ ] **Incomplete method override** — a subclass overrides a method but
      drops part of what the base behavior (or sibling subclasses)
      guarantees.
- [ ] **Shadowed field/variable in a constructor** — a constructor
      declares something with the same name as an inherited field,
      intending to set the field, but actually just creates a dead local
      variable that goes nowhere.
- [ ] **`equals`/`hashCode` inconsistency** — `hashCode` is computed from
      a field that isn't part of `equals`, and that field is mutable,
      breaking hash-based collection lookups after the object changes.
- [ ] **Wrong boolean operator in an interval check** — an "overlap"
      condition uses the wrong logical operator, making it far too
      permissive (or restrictive) about what counts as overlapping.
- [ ] **Incomplete recursive/backtracking search** — a recursive algorithm
      that's supposed to explore multiple possibilities only explores one
      path per step, so it can miss the actual best answer.
- [ ] **Wrong operator precedence** — a boolean expression mixing `&&` and
      `||` without parentheses groups differently than intended, letting
      a case slip through that should have been blocked.
- [ ] **Wrong quantity used in a calculation** — a monetary calculation
      that's supposed to be a flat amount is instead derived from an
      unrelated field, as if copied from a different calculation.
- [ ] **Missing state-machine guard** — an operation checks for one
      invalid state but not another, letting an action happen from a
      state it shouldn't be reachable from.
- [ ] **Stateful resource not tracked across a loop** — a batch process
      that hands out a limited resource to multiple requesters doesn't
      remove/mark a resource as taken once assigned, so it can be handed
      out twice.
- [ ] **Mutating a collection while iterating it** — a loop removes
      elements from the exact list it's iterating over, instead of using
      an iterator's own removal method (or an equivalent safe pattern).
- [ ] **Integer division where a decimal was needed** — a ratio is
      computed from two whole numbers without ever converting to a
      floating-point type, so the result gets truncated to a whole number.
- [ ] **Silently swallowed exception** — a `catch` block does nothing with
      the exception it catches — no logging, no reporting back to the
      caller.
- [ ] **Missing null check before dereferencing** — code assumes a
      reference is always set once a certain state is reached, without
      verifying it, and crashes when that assumption doesn't hold.
- [ ] **Wrong field (and wrong direction) in a sort comparator** — a
      ranking is supposed to be based on one field, descending, but the
      comparator is built on a completely different field, ascending.

## How to work through it
1. Read `PRD.md` fully — several of these bugs (the interval-overlap
   rule, the cancellation-fee waiver rule, the ride-pooling optimality
   requirement) only look wrong once you know the exact rule.
2. Run `Main` and read every `[FAIL]`/`[CRASHED]` line and its assertion
   message.
3. Fix one bug at a time, recompile, and rerun until all 17 scenarios
   show `[PASS]`. Don't edit `Main.java`'s assertions.
4. A few of these bugs are in files you might not think to check first
   (the `Vehicle` subclasses, `AvailabilityWindow`, `RidePoolMatcher`) —
   don't assume every bug lives in the main service class just because
   that was true in the last two exercises.
