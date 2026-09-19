package com.ridehail.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Driver {
    private final String id;
    private final String name;
    private final Vehicle vehicle;
    private double rating;
    private int completedTripCount;
    private final List<AvailabilityWindow> availability = new ArrayList<>();

    public Driver(String id, String name, Vehicle vehicle, double initialRating) {
        this.id = id;
        this.name = name;
        this.vehicle = vehicle;
        this.rating = initialRating;
        this.completedTripCount = 0;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public int getCompletedTripCount() {
        return completedTripCount;
    }

    public void incrementCompletedTripCount() {
        completedTripCount++;
    }

    public List<AvailabilityWindow> getAvailability() {
        return availability;
    }

    public void addAvailabilityWindow(AvailabilityWindow window) {
        availability.add(window);
    }

    public boolean isAvailableDuring(AvailabilityWindow proposed) {
        for (AvailabilityWindow existing : availability) {
            if (existing.overlaps(proposed)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Driver)) {
            return false;
        }
        Driver other = (Driver) o;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Driver{id=%s, name=%s, rating=%.2f}", id, name, rating);
    }
}
