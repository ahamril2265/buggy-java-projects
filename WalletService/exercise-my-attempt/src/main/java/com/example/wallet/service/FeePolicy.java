package com.example.wallet.service;

import com.example.wallet.config.WalletProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FeePolicy {

    private static final BigDecimal BASIS_POINTS_DIVISOR = BigDecimal.valueOf(10_000);

    private final WalletProperties.Fees fees;

    public FeePolicy(WalletProperties properties) {
        this.fees = properties.fees();
    }

    public long feeFor(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        long proportional = BigDecimal.valueOf(amountMinor)
                .multiply(BigDecimal.valueOf(fees.transferBasisPoints()))
                .divide(BASIS_POINTS_DIVISOR, 0, RoundingMode.HALF_UP)
                .longValueExact();
        return Math.max(proportional, fees.minimumMinor());
    }
}
