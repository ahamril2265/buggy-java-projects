package com.orchestrator.service;

import java.util.ArrayDeque;
import java.util.Deque;

public class RateLimiter {
    private final int maxEvents;
    private final long windowMs;
    private final Deque<Long> timestamps = new ArrayDeque<>();

    public RateLimiter(int maxEvents, long windowMs) {
        this.maxEvents = maxEvents;
        this.windowMs = windowMs;
    }

    public boolean tryAcquire(long nowMs) {
        while (!timestamps.isEmpty() && nowMs - timestamps.peekFirst() >= windowMs) {
            timestamps.pollFirst();
        }
        if (timestamps.size() >= maxEvents) {
            return false;
        }
        timestamps.addLast(nowMs);
        return true;
    }
}
