package com.orchestrator.service;

public class RetryPolicy {
    private final int baseDelayMs;
    private final int maxDelayMs;
    private final int maxRetries;

    public RetryPolicy(int baseDelayMs, int maxDelayMs, int maxRetries) {
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.maxRetries = maxRetries;
    }

    public boolean shouldRetry(int attemptsMade) {
        return attemptsMade <= maxRetries;
    }

    public int delayForRetry(int retryNumber) {
        int shift = Math.min(retryNumber - 1, 31);
        long delay = (long) baseDelayMs << shift;
        //System.out.println(2 ^ ( retryNumber - 1 ));
        return ( int ) Math.min(delay, maxDelayMs);
    }
}
