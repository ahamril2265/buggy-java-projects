package com.orchestrator.service;

import java.time.ZonedDateTime;

public class DailySchedule {

    public ZonedDateTime nextRun(ZonedDateTime lastRun) {
        return lastRun.plusDays(1);
    }
}
