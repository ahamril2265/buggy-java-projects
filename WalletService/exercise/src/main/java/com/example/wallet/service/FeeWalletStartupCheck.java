package com.example.wallet.service;

import com.example.wallet.config.WalletProperties;
import com.example.wallet.domain.Wallet;
import com.example.wallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Fail fast at startup if a supported currency has no fee wallet, instead of failing on the first transfer. */
@Component
public class FeeWalletStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FeeWalletStartupCheck.class);

    private final WalletRepository wallets;
    private final WalletProperties properties;

    public FeeWalletStartupCheck(WalletRepository wallets, WalletProperties properties) {
        this.wallets = wallets;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String currency : properties.supportedCurrencies()) {
            if (wallets.findByOwnerIdAndCurrency(Wallet.SYSTEM_OWNER, currency).isEmpty()) {
                throw new IllegalStateException("No fee wallet configured for supported currency " + currency);
            }
        }
        log.info("Fee wallets verified for currencies {}", properties.supportedCurrencies());
    }
}
