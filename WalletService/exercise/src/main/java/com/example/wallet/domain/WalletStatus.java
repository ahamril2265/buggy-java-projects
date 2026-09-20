package com.example.wallet.domain;

public enum WalletStatus {
    ACTIVE,
    FROZEN,
    CLOSED;

    public boolean canTransitionTo(WalletStatus target) {
        return switch (this) {
            case ACTIVE -> target == FROZEN || target == CLOSED;
            case FROZEN -> target == ACTIVE || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
