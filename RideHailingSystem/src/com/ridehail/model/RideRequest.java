package com.ridehail.model;

public class RideRequest {
    private final String id;
    private final int passengerCount;

    public RideRequest(String id, int passengerCount) {
        this.id = id;
        this.passengerCount = passengerCount;
    }

    public String getId() {
        return id;
    }

    public int getPassengerCount() {
        return passengerCount;
    }

    @Override
    public String toString() {
        return String.format("RideRequest{id=%s, passengers=%d}", id, passengerCount);
    }
}
