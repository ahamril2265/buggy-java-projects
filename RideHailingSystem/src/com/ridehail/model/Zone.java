package com.ridehail.model;

public class Zone {
    private final String name;
    private final int pendingRequests;
    private final int availableDrivers;
    private double surgeMultiplier = 1.0;

    public Zone(String name, int pendingRequests, int availableDrivers) {
        this.name = name;
        this.pendingRequests = pendingRequests;
        this.availableDrivers = availableDrivers;
    }

    public String getName() {
        return name;
    }

    public int getPendingRequests() {
        return pendingRequests;
    }

    public int getAvailableDrivers() {
        return availableDrivers;
    }

    public double getSurgeMultiplier() {
        return surgeMultiplier;
    }

    public void setSurgeMultiplier(double surgeMultiplier) {
        this.surgeMultiplier = surgeMultiplier;
    }
}
