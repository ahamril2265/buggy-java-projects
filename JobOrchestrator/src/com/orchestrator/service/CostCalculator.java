package com.orchestrator.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CostCalculator {

    public BigDecimal jobCost(double ratePerSecond, long seconds) {
        return BigDecimal.valueOf(ratePerSecond)
                .multiply(BigDecimal.valueOf(seconds))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
