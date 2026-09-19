# Product Requirements Document — Ride-Hailing / Trip Booking System

## 1. Overview
A backend service simulating the core of a ride-hailing platform: vehicle
types with different fare rates, driver availability scheduling, trip
matching (including ride-pooling), the trip lifecycle, cancellation fees,
surge pricing, and driver leaderboards.

Layered, no framework, no persistence:

```
Main (demo/test runner)
   -> TripService        (trip lifecycle, matching, pricing, stats)
   -> RidePoolMatcher     (standalone pooling algorithm)
       -> model classes (Vehicle hierarchy, Driver, Rider, Trip, AvailabilityWindow, RideRequest)
```

This exercise is harder than the previous two: it includes an inheritance
hierarchy, a recursive matching algorithm, and collections/equals-hashCode
pitfalls, on top of the business-rule-style bugs from before.

## 2. Domain Model

### 2.1 Vehicles
- `Vehicle` is an abstract base with a license plate and a seat
  `capacity`, plus an abstract `calculateFare(distanceMiles, durationMinutes)`.
- `StandardVehicle`: capacity 4. Fare = `$2.50 base + $1.25/mile + $0.20/min`.
- `XLVehicle`: capacity is provided by the caller when the vehicle is
  created (e.g. 6 or 7 seats) — it is **not** a fixed number. Fare =
  `$4.00 base + $1.75/mile + $0.30/min`.
- `PremiumVehicle`: capacity 4. Fare = `$6.00 base + $2.50/mile + $0.45/min`.
- FR-1: Every vehicle's fare **always includes its flat base fare** — there
  is no vehicle type that charges purely by distance and time.
- FR-2: `getCapacity()` must always return the actual seat count the
  vehicle was configured with, for every vehicle type.

### 2.2 Drivers
- A `Driver` has an id, name, a `Vehicle`, a mutable `rating` (updated
  over time), and a list of `AvailabilityWindow`s.
- FR-3: Two `Driver` objects are equal if and only if their ids match.
  Drivers must work correctly as keys/elements in `HashSet`/`HashMap` —
  including *after* a driver's rating changes.
- FR-4: `AvailabilityWindow` overlap detection: two windows overlap **only
  when they actually share time** — a window ending exactly when another
  begins does not count as an overlap, and two windows on completely
  separate days must never be reported as overlapping.

### 2.3 Trips
- FR-5: A trip has a lifecycle: `REQUESTED -> ACCEPTED -> IN_PROGRESS ->
  COMPLETED`, or `-> CANCELLED` from any non-terminal state.
- FR-6: A trip can only be marked `COMPLETED` if it is currently
  `IN_PROGRESS`. Completing a trip in any other state (including
  `REQUESTED`, `ACCEPTED`, or an already-`COMPLETED`/`CANCELLED` trip)
  must be rejected.
- FR-7: Cancellation fee: a flat **$5.00** if cancelled within 10 minutes
  of the scheduled pickup time, otherwise **$0.00**. The fee is a flat
  amount — it does not scale with trip distance.
- FR-8: **Fee waiver**: the flat cancellation fee is waived only when the
  rider is cancelling **quickly** (within 2 minutes of requesting) *and*
  either the trip is still `REQUESTED` or the rider is a first-time rider.
  A first-time rider who cancels late still owes the fee — being a
  first-time rider is not by itself a waiver.

### 2.4 Matching
- FR-9: When matching a batch of pending trips to available drivers, a
  driver assigned to one trip in the batch must **not** also be assigned
  to a second trip in the same batch. Each driver can only carry one
  active trip at a time.
- FR-10: **Ride pooling**: given a list of ride requests (each with a
  passenger count) and a vehicle's remaining seat capacity, the pooling
  algorithm must find a group of requests that (a) fits within capacity
  and (b) has the **maximum total passenger count** achievable — not just
  the first combination that happens to fit. (Skipping an earlier request
  can allow a better-fitting later combination; the algorithm must
  consider that.)

### 2.5 Pricing
- FR-11: Surge multiplier is based on the demand ratio
  `pendingRequests / availableDrivers`, computed as a true decimal ratio
  (e.g. 3 pending requests over 2 drivers is a ratio of **1.5**, not 1).
  - ratio <= 1.0 -> multiplier 1.0
  - ratio <= 1.5 -> multiplier 1.2
  - ratio <= 2.0 -> multiplier 1.5
  - ratio > 2.0 -> multiplier 2.0
- FR-12: Recalculating surge pricing is a batch job over all zones. If an
  individual zone's demand cannot be computed (e.g. it has no driver
  data), that failure must be **recorded and reported** as an error for
  that zone — never silently discarded. Other zones must still be
  processed.

### 2.6 Reporting
- FR-13: Computing driver stats must handle trips that have no assigned
  driver (e.g. cancelled before a driver was matched) without crashing —
  such trips are simply skipped.
- FR-14: `getTopDrivers` ranks drivers by **rating, highest first**. It
  never ranks by trip count or any other field.

## 3. Non-functional Requirements
- NFR-1: Expected failure conditions return a failure result, not an
  unchecked exception.
- NFR-2: Batch operations must report per-item errors rather than
  swallowing them.
- NFR-3: Iterating and removing from the same live collection must not
  throw `ConcurrentModificationException`.

## 4. Out of Scope
- Persistence, real geolocation/routing, payments, authentication,
  concurrency/threading.

## 5. Acceptance Criteria
`Main.java` runs these as PASS/FAIL/CRASHED scenarios — see the scenario
names in `Main.java` for the full list (vehicle fares and capacity,
overlap detection, trip lifecycle guard, cancellation fee + waiver rules,
double-booking prevention, ride-pool optimality, surge ratio math, the
silent-swallow batch job, driver stats with an unassigned-driver trip,
the leaderboard ordering, and the equals/hashCode-after-mutation case).
