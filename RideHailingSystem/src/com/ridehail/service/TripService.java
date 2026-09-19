package com.ridehail.service;

import com.ridehail.model.Driver;
import com.ridehail.model.Rider;
import com.ridehail.model.Trip;
import com.ridehail.model.TripStatus;
import com.ridehail.model.Zone;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TripService {

    private static final double CANCELLATION_FEE = 5.00;
    private static final long CANCELLATION_FEE_WINDOW_MINUTES = 10;
    private static final long QUICK_CANCEL_WINDOW_MINUTES = 2;
    private static final long STALE_REQUEST_MINUTES = 10;

    public ServiceResult completeTrip(Trip trip, LocalDateTime now) {
        if (trip == null) {
            return ServiceResult.failure("Unknown trip");
        }
        if (trip.getStatus() != TripStatus.IN_PROGRESS) { 
            return ServiceResult.failure("Trip must be IN_PROGRESS to complete, but is " + trip.getStatus()); 
        }

        double fare = trip.getDriver().getVehicle().calculateFare(trip.getDistanceMiles(), trip.getDurationMinutes());
        trip.setFare(fare);
        trip.setStatus(TripStatus.COMPLETED);
        trip.getDriver().incrementCompletedTripCount();
        return ServiceResult.success("Trip completed, fare " + fare);
    }

    public double calculateCancellationFee(Trip trip, LocalDateTime cancelTime) {
        boolean feeWaived = Duration.between(trip.getRequestedTime(), cancelTime).toMinutes() <= QUICK_CANCEL_WINDOW_MINUTES && ( trip.getStatus() == TripStatus.REQUESTED
                || trip.getRider().isFirstTimeRider() ) ;
        if (feeWaived) {
            return 0.0;
        }

        long minutesUntilPickup = Duration.between(cancelTime, trip.getScheduledPickupTime()).toMinutes();
        if (minutesUntilPickup > CANCELLATION_FEE_WINDOW_MINUTES) {
            return 0.0;
        }
        return CANCELLATION_FEE;
    }

    public ServiceResult cancelTrip(Trip trip, LocalDateTime now) {
        if (trip == null) {
            return ServiceResult.failure("Unknown trip"); 
        }
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            return ServiceResult.failure("Cannot cancel a trip in state " + trip.getStatus());
        }
        double fee = calculateCancellationFee(trip, now);
        trip.setStatus(TripStatus.CANCELLED);
        return ServiceResult.success("Trip cancelled, fee " + fee);
    }

    public List<ServiceResult> matchPendingTrips(List<Trip> pendingTrips, List<Driver> availableDrivers) {
        List<ServiceResult> results = new ArrayList<>();
        for (Trip trip : pendingTrips) {
            Driver bestDriver = findBestDriver(availableDrivers, trip);
            if (bestDriver != null) {
                trip.assignDriver(bestDriver);
                results.add(ServiceResult.success("Matched trip " + trip.getId() + " to driver " + bestDriver.getId()));
                availableDrivers.remove(bestDriver);
            } else {
                results.add(ServiceResult.failure("No driver available for trip " + trip.getId()));
            }
        }
        return results;
    }

    private Driver findBestDriver(List<Driver> availableDrivers, Trip trip) {
        for (Driver driver : availableDrivers) {
            return driver;
        }
        return null;
    }

    public void expireStaleRequests(List<Trip> pendingTrips, LocalDateTime now) {
        Iterator<Trip> it = pendingTrips.iterator();
        while (it.hasNext()) {
            Trip trip = it.next();
            if (trip.getStatus() == TripStatus.REQUESTED
                    && Duration.between(trip.getRequestedTime(), now).toMinutes() > STALE_REQUEST_MINUTES) {
                it.remove();
                trip.setStatus(TripStatus.CANCELLED);
            }
        }
    }

    private double computeDemandRatio(Zone zone) {
        if (zone.getAvailableDrivers() == 0) {
            throw new ArithmeticException("No drivers available in zone: " + zone.getName());
        }
        int pending = zone.getPendingRequests();
        int available = zone.getAvailableDrivers();
        double ratio = ( double ) pending / available;
        return ratio;
    }

    private double getSurgeMultiplier(double demandRatio) {
        if (demandRatio <= 1.0) {
            return 1.0;
        }
        if (demandRatio > 1.0 && demandRatio <= 1.5) {
            return 1.2;
        }
        if (demandRatio > 1.5 && demandRatio <= 2.0) {
            return 1.5;
        }
        return 2.0;
    }

    public List<String> recalculateAllSurgePricing(List<Zone> zones) {
        List<String> errors = new ArrayList<>();
        for (Zone zone : zones) {
            try {
                if ( zone.getAvailableDrivers() == 0 ) { 
                    errors.add("No driver data for zone: " + zone.getName());
                    continue; 
                } 
                double ratio = computeDemandRatio(zone);
                zone.setSurgeMultiplier(getSurgeMultiplier(ratio));
            } catch (Exception e) {
                errors.add("Error in zone " + zone.getName() + ": " + e.getMessage());
            }
        }
        return errors;
    }

    public Map<String, Integer> getDriverStats(List<Trip> trips) {
        Map<String, Integer> stats = new HashMap<>();
        for (Trip trip : trips) {
            if (trip.getStatus() != TripStatus.COMPLETED) {
                continue;
            }
            Driver driver = trip.getDriver();
            if (driver == null) {
                continue;
            }
            stats.merge(driver.getId(), 1, Integer::sum);
        }
        return stats;
    }
    
    public List<Driver> getTopDrivers(List<Driver> drivers) {
        List<Driver> sorted = new ArrayList<>(drivers);
        sorted.sort(Comparator.comparingDouble(Driver::getRating).reversed());
        return sorted;
    }

    public static class ServiceResult {
        private final boolean success;
        private final String message;

        private ServiceResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ServiceResult success(String message) {
            return new ServiceResult(true, message);
        }

        public static ServiceResult failure(String message) {
            return new ServiceResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
