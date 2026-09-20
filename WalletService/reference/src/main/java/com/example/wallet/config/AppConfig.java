package com.example.wallet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AppConfig {

    /** All business time comes from this clock so it can be controlled in tests. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
