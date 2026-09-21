package com.example.wallet.service;

public record IdempotentResult<T>(int status, T body, boolean replayed) {
}
