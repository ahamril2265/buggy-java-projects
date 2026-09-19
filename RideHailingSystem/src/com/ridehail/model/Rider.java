package com.ridehail.model;

public class Rider {
    private final String id;
    private final String name;
    private final boolean firstTimeRider;

    public Rider(String id, String name, boolean firstTimeRider) {
        this.id = id;
        this.name = name;
        this.firstTimeRider = firstTimeRider;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isFirstTimeRider() {
        return firstTimeRider;
    }
}
