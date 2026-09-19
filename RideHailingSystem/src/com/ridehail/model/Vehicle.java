package com.ridehail.model;

public abstract class Vehicle {
    protected final String licensePlate;
    protected final int capacity;

    protected Vehicle(String licensePlate, int capacity) {
        this.licensePlate = licensePlate;
        this.capacity = capacity;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public int getCapacity() {
        return capacity;
    }

    public abstract double calculateFare(double distanceMiles, double durationMinutes);
}
