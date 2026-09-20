package com.orchestrator.service;

import java.util.concurrent.atomic.AtomicInteger;

public class JobMetrics {
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);

    public void recordCompleted() {
        completed.incrementAndGet();
    }

    public void recordFailed() {
        failed.incrementAndGet();
    }

    public int getCompleted() {
        return completed.get();
    }

    public int getFailed() {
        return failed.get();
    }
}
