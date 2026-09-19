# Ride-Hailing / Trip Booking System (Debugging Exercise — Hardest Yet)

A layered Java service simulating a ride-hailing platform: a `Vehicle`
inheritance hierarchy with per-type fare rules, driver availability
scheduling, trip matching (including ride-pooling via a recursive
algorithm), the trip lifecycle, cancellation fees, surge pricing, and
driver leaderboards. This is the third and hardest exercise — 14 seeded
bugs, including inheritance/polymorphism pitfalls, an incomplete
recursive search, and an equals/hashCode collections gotcha, on top of
the state-machine/rounding/atomicity-style bugs from the last two
projects.

## Structure
```
PRD.md                     - the spec: fare rules, overlap rules, lifecycle, fee/waiver rules, pooling optimality, surge math
BUG_CHECKLIST.md           - categories of bugs seeded in this project (no locations)
src/
  Main.java                - scenario runner; prints PASS/FAIL/CRASHED per scenario
  com/ridehail/model/      - Vehicle (+ StandardVehicle/XLVehicle/PremiumVehicle), Driver, Rider,
                             Trip, TripStatus, AvailabilityWindow, RideRequest, Zone
  com/ridehail/service/    - TripService (lifecycle, matching, pricing, stats),
                             RidePoolMatcher (recursive ride-pooling algorithm)
```

## How to compile & run

From this directory:

```bash
javac -d out src/com/ridehail/model/*.java src/com/ridehail/service/*.java src/Main.java
java -cp out Main
```

## What "done" looks like
`Main` runs 17 scenarios derived from the PRD's acceptance criteria.
Currently 15 fail or crash. Your job is to read `PRD.md`, understand what
each scenario should do, find the bug(s) causing the mismatch, fix them,
and get all 17 scenarios to `[PASS]` without changing `Main.java`'s
assertions.

`BUG_CHECKLIST.md` lists the 14 categories of bugs seeded here if you
want a hint about *what kind* of mistake to look for without being told
where.

This round spreads bugs across more files than before — the `Vehicle`
subclasses and `AvailabilityWindow` each carry one, not just the service
class — so don't assume everything lives in `TripService.java` just
because that's where most of the bugs lived last time.
