package com.ridehail.model;

public class XLVehicle extends Vehicle {
    private static final double BASE_FARE = 4.00;
    private static final double PER_MILE = 1.75;
    private static final double PER_MINUTE = 0.30;

    public XLVehicle(String licensePlate, int seatCount) {
        int capacity = seatCount;
        super(licensePlate, capacity);
        
    }

    @Override
    public double calculateFare(double distanceMiles, double durationMinutes) {
        return BASE_FARE + (distanceMiles * PER_MILE) + (durationMinutes * PER_MINUTE);
    }
}
