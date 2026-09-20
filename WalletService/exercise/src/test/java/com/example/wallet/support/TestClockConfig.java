package com.example.wallet.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    public static final Instant BASE_TIME = Instant.parse("2026-01-15T10:00:00Z");

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(BASE_TIME);
    }
}
