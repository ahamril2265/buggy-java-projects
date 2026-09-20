package com.example.wallet.domain;

import com.example.wallet.config.WalletProperties;
import com.example.wallet.service.FeePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeePolicyTest {

    private static FeePolicy policy(int basisPoints, long minimumMinor) {
        return new FeePolicy(new WalletProperties(List.of("USD"),
                new WalletProperties.Fees(basisPoints, minimumMinor),
                new WalletProperties.Limits(1_000_000, 500_000)));
    }

    private final FeePolicy policy = policy(50, 1);

    @Test
    void feeIsHalfAPercentOfTheAmount() {
        assertThat(policy.feeFor(1_000)).isEqualTo(5);
        assertThat(policy.feeFor(20_000)).isEqualTo(100);
    }

    @Test
    void exactHalfCentRoundsUp() {
        // 1_100 * 0.5% = 5.5 -> 6 (round half up)
        assertThat(policy.feeFor(1_100)).isEqualTo(6);
    }

    @Test
    void roundsToTheNearestMinorUnit() {
        assertThat(policy.feeFor(1_099)).isEqualTo(5);   // 5.495
        assertThat(policy.feeFor(1_101)).isEqualTo(6);   // 5.505
    }

    @Test
    void minimumFeeAppliesToTinyAmounts() {
        assertThat(policy.feeFor(1)).isEqualTo(1);
        assertThat(policy.feeFor(10)).isEqualTo(1);      // 0.05 -> 0, raised to the minimum
        assertThat(policy(50, 3).feeFor(10)).isEqualTo(3);
    }

    @Test
    void minimumFeeDoesNotInflateLargeFees() {
        assertThat(policy(50, 3).feeFor(20_000)).isEqualTo(100);
    }

    @Test
    void doesNotOverflowForHugeAmounts() {
        assertThat(policy.feeFor(9_000_000_000_000_000_000L)).isEqualTo(45_000_000_000_000_000L);
    }

    @Test
    void zeroBasisPointsAndZeroMinimumMeansNoFee() {
        assertThat(policy(0, 0).feeFor(5_000)).isZero();
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> policy.feeFor(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.feeFor(-5)).isInstanceOf(IllegalArgumentException.class);
    }
}
