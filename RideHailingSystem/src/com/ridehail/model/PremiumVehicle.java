package com.ridehail.model;

public class PremiumVehicle extends Vehicle {
    private static final double BASE_FARE = 6.00;
    private static final double PER_MILE = 2.50;
    private static final double PER_MINUTE = 0.45;

    public PremiumVehicle(String licensePlate) {
        super(licensePlate, 4);
    }

    @Override
    public double calculateFare(double distanceMiles, double durationMinutes) {
        return 6.00 + (distanceMiles * PER_MILE) + (durationMinutes * PER_MINUTE);
    }
}
