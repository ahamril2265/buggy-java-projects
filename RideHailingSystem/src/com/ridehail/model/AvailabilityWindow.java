package com.ridehail.model;

import java.time.LocalDateTime;

public class AvailabilityWindow {
    private final LocalDateTime start;
    private final LocalDateTime end;

    public AvailabilityWindow(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public boolean overlaps(AvailabilityWindow other) {
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    @Override
    public String toString() {
        return String.format("[%s - %s]", start, end);
    }
}
