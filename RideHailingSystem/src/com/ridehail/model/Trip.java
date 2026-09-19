package com.ridehail.model;

import java.time.LocalDateTime;

public class Trip {
    private final String id;
    private final Rider rider;
    private Driver driver;
    private final double distanceMiles;
    private final double durationMinutes;
    private final LocalDateTime requestedTime;
    private final LocalDateTime scheduledPickupTime;
    private TripStatus status;
    private double fare;

    public Trip(String id, Rider rider, double distanceMiles, double durationMinutes,
                LocalDateTime requestedTime, LocalDateTime scheduledPickupTime) {
        this.id = id;
        this.rider = rider;
        this.distanceMiles = distanceMiles;
        this.durationMinutes = durationMinutes;
        this.requestedTime = requestedTime;
        this.scheduledPickupTime = scheduledPickupTime;
        this.status = TripStatus.REQUESTED;
        this.fare = 0.0;
    }

    public String getId() {
        return id;
    }

    public Rider getRider() {
        return rider;
    }

    public Driver getDriver() {
        return driver;
    }

    public void assignDriver(Driver driver) {
        this.driver = driver;
        this.status = TripStatus.ACCEPTED;
    }

    public double getDistanceMiles() {
        return distanceMiles;
    }

    public double getDurationMinutes() {
        return durationMinutes;
    }

    public LocalDateTime getRequestedTime() {
        return requestedTime;
    }

    public LocalDateTime getScheduledPickupTime() {
        return scheduledPickupTime;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    public double getFare() {
        return fare;
    }

    public void setFare(double fare) {
        this.fare = fare;
    }

    @Override
    public String toString() {
        return String.format("Trip{id=%s, status=%s, fare=%.2f}", id, status, fare);
    }
}
