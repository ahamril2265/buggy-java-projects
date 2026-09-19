package com.bank.model;

import java.util.List;
import java.util.ArrayList;

public class Account {

    private final String id;
    private final String ownerName;
    private final AccountType type;
    private AccountStatus status;
    private double balance;
    private final List<Transaction> transactions;

    public Account(String id, String ownerName, AccountType type, List<Transaction> transactions) {
        this.id = id;
        this.ownerName = ownerName;
        this.type = type;
        this.status = AccountStatus.ACTIVE;
        this.balance = 0.0;
        this.transactions = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public AccountType getType() {
        return type;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public double getBalance() {
        return balance;
    }

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        balance = transaction.getBalanceAfter();
    }

    public List<Transaction> getTransactions() {
        return List.copyOf(transactions);
    }

    @Override
    public String toString() {
        return String.format("Account{id=%s, owner=%s, type=%s, status=%s, balance=%.2f}",
                id, ownerName, type, status, balance);
    }
}
