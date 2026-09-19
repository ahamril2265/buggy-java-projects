import com.ridehail.model.AvailabilityWindow;
import com.ridehail.model.Driver;
import com.ridehail.model.PremiumVehicle;
import com.ridehail.model.RideRequest;
import com.ridehail.model.Rider;
import com.ridehail.model.StandardVehicle;
import com.ridehail.model.Trip;
import com.ridehail.model.TripStatus;
import com.ridehail.model.Vehicle;
import com.ridehail.model.XLVehicle;
import com.ridehail.model.Zone;
import com.ridehail.service.RidePoolMatcher;
import com.ridehail.service.TripService;
import com.ridehail.service.TripService.ServiceResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demo/test runner for the Ride-Hailing / Trip Booking System.
 * Each scenario prints PASS, FAIL, or CRASHED. A FAIL or CRASHED means
 * there's a bug to find in the corresponding model/service code.
 */
public class Main {

    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) {
        System.out.println("=== Ride-Hailing System - Scenario Runner ===\n");

        scenario1_standardVehicleFare();
        scenario2_premiumVehicleIncludesBaseFare();
        scenario3_xlVehicleCapacityMatchesConstructorArg();
        scenario4_overlappingWindowsDetected();
        scenario5_nonOverlappingWindowsNotDetected();
        scenario6_adjacentWindowsDoNotOverlap();
        scenario7_driverEqualsHashCodeSurvivesRatingChange();
        scenario8_matchingDoesNotDoubleBookADriver();
        scenario9_ridePoolFindsMaxPassengerGroup();
        scenario10_cannotCompleteTripNotInProgress();
        scenario11_lateCancellationFlatFee();
        scenario12_firstTimeRiderLateCancelStillOwesFee();
        scenario13_expireStaleRequestsNoCrash();
        scenario14_surgeRatioIsDecimalNotTruncated();
        scenario15_surgeBatchReportsZoneError();
        scenario16_driverStatsSkipsUnassignedDriver();
        scenario17_leaderboardRanksByRating();

        System.out.println("\n=== Summary: " + passCount + " passed, " + failCount + " failed ===");
    }

    private static void scenario1_standardVehicleFare() {
        run("Scenario 1: StandardVehicle fare includes base + distance + time", () -> {
            Vehicle vehicle = new StandardVehicle("STD-1");
            double fare = vehicle.calculateFare(10.0, 20.0);
            double expected = 2.50 + (10.0 * 1.25) + (20.0 * 0.20);
            check(Math.abs(fare - expected) < 0.001, "expected fare " + expected + ", got " + fare);
        });
    }

    private static void scenario2_premiumVehicleIncludesBaseFare() {
        run("Scenario 2: PremiumVehicle fare includes its base fare", () -> {
            Vehicle vehicle = new PremiumVehicle("PREM-1");
            double fare = vehicle.calculateFare(10.0, 20.0);
            double expected = 6.00 + (10.0 * 2.50) + (20.0 * 0.45);
            check(Math.abs(fare - expected) < 0.001, "expected fare " + expected + " (including $6.00 base), got " + fare);
        });
    }

    private static void scenario3_xlVehicleCapacityMatchesConstructorArg() {
        run("Scenario 3: XLVehicle capacity matches the constructor argument", () -> {
            Vehicle vehicle = new XLVehicle("XL-1", 8);
            check(vehicle.getCapacity() == 8, "expected capacity 8, got " + vehicle.getCapacity());
        });
    }

    private static void scenario4_overlappingWindowsDetected() {
        run("Scenario 4: Genuinely overlapping windows are detected", () -> {
            AvailabilityWindow a = new AvailabilityWindow(at(9, 0), at(11, 0));
            AvailabilityWindow b = new AvailabilityWindow(at(10, 0), at(12, 0));
            check(a.overlaps(b), "expected windows 9-11 and 10-12 to overlap");
        });
    }

    private static void scenario5_nonOverlappingWindowsNotDetected() {
        run("Scenario 5: Non-overlapping windows are not falsely flagged", () -> {
            AvailabilityWindow a = new AvailabilityWindow(at(9, 0), at(10, 0));
            AvailabilityWindow b = new AvailabilityWindow(at(13, 0), at(14, 0));
            check(!a.overlaps(b), "expected windows 9-10 and 13-14 to NOT overlap, but overlaps() returned true");
        });
    }

    private static void scenario6_adjacentWindowsDoNotOverlap() {
        run("Scenario 6: Back-to-back (adjacent) windows do not overlap", () -> {
            AvailabilityWindow a = new AvailabilityWindow(at(9, 0), at(10, 0));
            AvailabilityWindow b = new AvailabilityWindow(at(10, 0), at(11, 0));
            check(!a.overlaps(b), "expected adjacent windows 9-10 and 10-11 to NOT overlap, but overlaps() returned true");
        });
    }

    private static void scenario7_driverEqualsHashCodeSurvivesRatingChange() {
        run("Scenario 7: Driver lookup in a HashSet survives a rating change", () -> {
            Driver driver = new Driver("d1", "Alice", new StandardVehicle("STD-1"), 4.5);
            Set<Driver> set = new HashSet<>();
            set.add(driver);

            driver.setRating(3.9);

            check(set.contains(driver), "expected the driver to still be found in the set after its rating changed");
        });
    }

    private static void scenario8_matchingDoesNotDoubleBookADriver() {
        run("Scenario 8: Matching never assigns the same driver to two trips", () -> {
            TripService service = new TripService();
            Driver driver = new Driver("d1", "Alice", new StandardVehicle("STD-1"), 4.5);
            List<Driver> availableDrivers = new ArrayList<>();
            availableDrivers.add(driver);

            Rider riderA = new Rider("r1", "Bob", false);
            Rider riderB = new Rider("r2", "Carla", false);
            Trip tripA = new Trip("t1", riderA, 5.0, 15.0, LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));
            Trip tripB = new Trip("t2", riderB, 3.0, 10.0, LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));

            List<Trip> pending = new ArrayList<>();
            pending.add(tripA);
            pending.add(tripB);

            service.matchPendingTrips(pending, availableDrivers);

            boolean bothAssignedSameDriver = tripA.getDriver() != null && tripB.getDriver() != null
                    && tripA.getDriver().equals(tripB.getDriver());
            check(!bothAssignedSameDriver, "expected only one trip to be matched to the single available driver, but both were");
        });
    }

    private static void scenario9_ridePoolFindsMaxPassengerGroup() {
        run("Scenario 9: Ride pooling finds the maximum-passenger group, not just the first fit", () -> {
            RidePoolMatcher matcher = new RidePoolMatcher();
            List<RideRequest> requests = new ArrayList<>();
            requests.add(new RideRequest("req-1", 3));
            requests.add(new RideRequest("req-2", 2));
            requests.add(new RideRequest("req-3", 2));

            List<RideRequest> best = matcher.findBestGroup(requests, 4);
            int total = matcher.totalPassengers(best);

            check(total == 4, "expected the optimal group to carry 4 passengers (req-2 + req-3), got " + total);
        });
    }

    private static void scenario10_cannotCompleteTripNotInProgress() {
        run("Scenario 10: Completing a trip that hasn't started (ACCEPTED) is rejected", () -> {
            TripService service = new TripService();
            Rider rider = new Rider("r1", "Bob", false);
            Trip trip = new Trip("t1", rider, 5.0, 15.0, LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));
            Driver driver = new Driver("d1", "Alice", new StandardVehicle("STD-1"), 4.5);
            trip.assignDriver(driver); // status becomes ACCEPTED, not IN_PROGRESS

            ServiceResult result = service.completeTrip(trip, LocalDateTime.now());

            check(!result.isSuccess(), "expected completing an ACCEPTED (not IN_PROGRESS) trip to be rejected, but got: " + result.getMessage());
        });
    }

    private static void scenario11_lateCancellationFlatFee() {
        run("Scenario 11: Late cancellation with no waiver incurs the flat $5.00 fee", () -> {
            TripService service = new TripService();
            Rider rider = new Rider("r1", "Bob", false); // not first-time
            LocalDateTime requestedTime = LocalDateTime.now().minusMinutes(30);
            LocalDateTime pickupTime = LocalDateTime.now().plusMinutes(1); // cancelling within the fee window
            Trip trip = new Trip("t1", rider, 20.0, 15.0, requestedTime, pickupTime);
            trip.setStatus(TripStatus.ACCEPTED); // not a quick REQUESTED cancel

            double fee = service.calculateCancellationFee(trip, LocalDateTime.now());

            check(Math.abs(fee - 5.00) < 0.001, "expected the flat $5.00 cancellation fee, got $" + fee);
        });
    }

    private static void scenario12_firstTimeRiderLateCancelStillOwesFee() {
        run("Scenario 12: A first-time rider who cancels late still owes the fee", () -> {
            TripService service = new TripService();
            Rider rider = new Rider("r1", "Bob", true); // first-time rider
            LocalDateTime requestedTime = LocalDateTime.now().minusMinutes(30); // not a quick cancel
            LocalDateTime pickupTime = LocalDateTime.now().plusMinutes(1);
            Trip trip = new Trip("t1", rider, 20.0, 15.0, requestedTime, pickupTime);
            trip.setStatus(TripStatus.ACCEPTED);

            double fee = service.calculateCancellationFee(trip, LocalDateTime.now());

            check(fee > 0, "expected a first-time rider cancelling late to still owe a fee, but fee was $" + fee);
        });
    }

    private static void scenario13_expireStaleRequestsNoCrash() {
        run("Scenario 13: Expiring stale requests does not crash", () -> {
            Rider rider = new Rider("r1", "Bob", false);
            LocalDateTime longAgo = LocalDateTime.now().minusMinutes(30);
            LocalDateTime recent = LocalDateTime.now().minusMinutes(2);

            Trip stale1 = new Trip("t1", rider, 5.0, 10.0, longAgo, longAgo.plusMinutes(15));
            Trip stale2 = new Trip("t2", rider, 5.0, 10.0, longAgo, longAgo.plusMinutes(15));
            Trip fresh = new Trip("t3", rider, 5.0, 10.0, recent, recent.plusMinutes(15));

            List<Trip> pending = new ArrayList<>();
            pending.add(stale1);
            pending.add(stale2);
            pending.add(fresh);

            TripService service = new TripService();
            service.expireStaleRequests(pending, LocalDateTime.now());

            check(pending.size() == 1, "expected only the fresh request to remain pending, got " + pending.size());
            check(stale1.getStatus() == TripStatus.CANCELLED, "expected stale1 to be cancelled");
            check(stale2.getStatus() == TripStatus.CANCELLED, "expected stale2 to be cancelled");
        });
    }

    private static void scenario14_surgeRatioIsDecimalNotTruncated() {
        run("Scenario 14: Surge ratio is computed as a true decimal (3 requests / 2 drivers = 1.5)", () -> {
            TripService service = new TripService();
            Zone zone = new Zone("downtown", 3, 2);
            List<Zone> zones = new ArrayList<>();
            zones.add(zone);

            service.recalculateAllSurgePricing(zones);

            check(Math.abs(zone.getSurgeMultiplier() - 1.2) < 0.001,
                    "expected surge multiplier 1.2 (ratio 1.5), got " + zone.getSurgeMultiplier());
        });
    }

    private static void scenario15_surgeBatchReportsZoneError() {
        run("Scenario 15: Surge batch reports an error for a zone with no driver data", () -> {
            TripService service = new TripService();
            Zone goodZone = new Zone("downtown", 3, 2);
            Zone badZone = new Zone("suburbs", 5, 0);
            List<Zone> zones = new ArrayList<>();
            zones.add(goodZone);
            zones.add(badZone);

            List<String> errors = service.recalculateAllSurgePricing(zones);

            check(errors.size() == 1, "expected exactly 1 error reported for the zone with no drivers, got " + errors.size());
        });
    }

    private static void scenario16_driverStatsSkipsUnassignedDriver() {
        run("Scenario 16: Driver stats skip a completed trip with no assigned driver", () -> {
            Rider rider = new Rider("r1", "Bob", false);
            Driver driver = new Driver("d1", "Alice", new StandardVehicle("STD-1"), 4.5);

            Trip normalTrip = new Trip("t1", rider, 5.0, 10.0, LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));
            normalTrip.assignDriver(driver);
            normalTrip.setStatus(TripStatus.COMPLETED);

            Trip orphanTrip = new Trip("t2", rider, 5.0, 10.0, LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));
            orphanTrip.setStatus(TripStatus.COMPLETED); // completed but never assigned a driver

            List<Trip> trips = new ArrayList<>();
            trips.add(normalTrip);
            trips.add(orphanTrip);

            TripService service = new TripService();
            Map<String, Integer> stats = service.getDriverStats(trips);

            check(stats.getOrDefault("d1", 0) == 1, "expected driver d1 to have 1 completed trip, got " + stats.getOrDefault("d1", 0));
        });
    }

    private static void scenario17_leaderboardRanksByRating() {
        run("Scenario 17: Leaderboard ranks drivers by rating, not trip count", () -> {
            TripService service = new TripService();
            Driver topRated = new Driver("d1", "Alice", new StandardVehicle("STD-1"), 4.9);
            for (int i = 0; i < 10; i++) {
                topRated.incrementCompletedTripCount();
            }
            Driver lowerRated = new Driver("d2", "Bob", new StandardVehicle("STD-2"), 3.0);
            lowerRated.incrementCompletedTripCount();
            lowerRated.incrementCompletedTripCount();

            List<Driver> drivers = new ArrayList<>();
            drivers.add(lowerRated);
            drivers.add(topRated);

            List<Driver> ranked = service.getTopDrivers(drivers);

            check(ranked.get(0).getId().equals("d1"), "expected the higher-rated driver (d1, rating 4.9) first, got " + ranked.get(0).getId());
        });
    }

    // ---- test harness helpers ----

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 1, 1, hour, minute);
    }

    private interface ScenarioBody {
        void run();
    }

    private static void run(String name, ScenarioBody body) {
        try {
            scenarioFailedFlag = false;
            body.run();
            if (scenarioFailedFlag) {
                failCount++;
                System.out.println("[FAIL] " + name);
            } else {
                passCount++;
                System.out.println("[PASS] " + name);
            }
        } catch (Exception e) {
            failCount++;
            System.out.println("[CRASHED] " + name + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static boolean scenarioFailedFlag = false;

    private static void check(boolean condition, String failureMessage) {
        if (!condition) {
            scenarioFailedFlag = true;
            System.out.println("        assertion failed: " + failureMessage);
        }
    }
}
