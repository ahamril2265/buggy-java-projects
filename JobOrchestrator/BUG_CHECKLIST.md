# Bug Checklist

There are **18 intentional bugs** in this codebase. Unlike the previous
exercises, most are **language/library pitfalls** rather than plain
business-rule mistakes, and they are spread across many small classes. No
locations given — use `PRD.md` plus the `[FAIL]`/`[CRASHED]` output from
`Main`.

- [ ] **Graph traversal state** — cycle detection can't tell "currently
      being explored" apart from "already fully explored", so valid
      shared dependencies are reported as cycles.
- [ ] **Integer overflow in a comparator** — a comparator built on
      subtraction returns the wrong sign for extreme values.
- [ ] **Integer overflow before a cap** — a value is computed in `int`
      arithmetic and only then clamped, but it has already wrapped
      around by the time the clamp runs (and a shift amount wraps too).
- [ ] **Off-by-one in a retry limit** — the number of allowed attempts
      doesn't match "1 initial attempt + N retries".
- [ ] **A cache that isn't the kind it claims to be** — an eviction policy
      is configured incorrectly, so it behaves like a different policy.
- [ ] **Window boundary condition** — a sliding-window check treats an
      event exactly on the boundary as still inside the window.
- [ ] **Reference equality on strings** — `==` used where value equality
      is needed; passes for literals, fails for strings built at runtime.
- [ ] **Reference equality on boxed numbers** — `==` on `Integer` objects;
      works for small values because of caching and silently fails for
      larger ones.
- [ ] **Inexact decimal construction** — money computed from a binary
      floating-point literal instead of its decimal text form.
- [ ] **Data race** — a shared counter updated from several threads with
      no synchronization/atomicity.
- [ ] **Aliasing a mutable argument** — a constructor keeps a reference to
      the caller's collection instead of its own copy.
- [ ] **Exception escaping a notification loop** — one misbehaving
      subscriber aborts delivery to everyone after it, and the failure is
      never recorded.
- [ ] **`compareTo` inconsistent with `equals`** — a sorted set silently
      drops distinct elements that merely compare as equal.
- [ ] **Time-zone arithmetic** — "one day later" computed as a fixed
      number of hours, which drifts across a daylight-saving change.
- [ ] **Stream collector contract** — a collector that assumes keys are
      unique throws when they aren't.
- [ ] **Regex metacharacter** — a delimiter passed to `String.split` is
      interpreted as a regular expression (and trailing empty fields
      matter too).
- [ ] **Non-transitive propagation** — a failure is propagated to direct
      dependents only, not to everything downstream.
- [ ] **Quantifier mix-up** — "any" used where "all" was required, so a
      job becomes ready too early.

## How to work through it
1. Read `PRD.md` first; several rules (window boundaries, retry counts,
   the DST requirement) are stated precisely there.
2. Run `Main`. A `[CRASHED]` line names the exception — often the
   quickest clue. A `[FAIL]` line shows expected vs. actual.
3. Fix one bug at a time and re-run. 4 scenarios are baseline checks that
   already pass; keep them passing.
4. Don't just patch the value in the test: for example, a wrong-looking
   number caused by overflow needs the arithmetic fixed, not the input.
5. Scenario 12 is a real multithreading test — run it a few times after
   your fix, a race can occasionally hide.
