package com.ridehail.model;

public class StandardVehicle extends Vehicle {
    private static final double BASE_FARE = 2.50;
    private static final double PER_MILE = 1.25;
    private static final double PER_MINUTE = 0.20;

    public StandardVehicle(String licensePlate) {
        super(licensePlate, 4);
    }

    @Override
    public double calculateFare(double distanceMiles, double durationMinutes) {
        return BASE_FARE + (distanceMiles * PER_MILE) + (durationMinutes * PER_MINUTE);
    }
}
