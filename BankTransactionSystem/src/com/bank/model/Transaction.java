package com.bank.model;

import java.time.LocalDate;

public class Transaction {
    private final String id;
    private final String accountId;
    private final TransactionType type;
    private final double amount;
    private final LocalDate timestamp;
    private final double balanceAfter;

    public Transaction(String id, String accountId, TransactionType type, double amount,
                        LocalDate timestamp, double balanceAfter) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp;
        this.balanceAfter = balanceAfter;
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDate getTimestamp() {
        return timestamp;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }

    @Override
    public String toString() {
        return String.format("Transaction{id=%s, account=%s, type=%s, amount=%.2f, date=%s, balanceAfter=%.2f}",
                id, accountId, type, amount, timestamp, balanceAfter);
    }
}
