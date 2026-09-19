package com.bank.service;

import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.repository.AccountRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BankService {

    private static final double CHECKING_OVERDRAFT_LIMIT = 500.0;
    private static final double MONTHLY_INTEREST_RATE = 0.005;
    private static final double CLOSED_WITHDRAWAL_REVIEW_THRESHOLD = 1000.0;

    private final AccountRepository accountRepository;
    private int transactionCounter = 0;
    // Reused as the starting transaction list for newly opened accounts.
    private final List<Transaction> newAccountTransactionBuffer = new ArrayList<>();

    public BankService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account openAccount(String accountId, String ownerName, AccountType type, double initialDeposit, LocalDate today) {
        Account account = new Account(accountId, ownerName, type, newAccountTransactionBuffer);
        accountRepository.save(account);
        if (initialDeposit > 0) {
            recordTransaction(account, TransactionType.DEPOSIT, initialDeposit, today, account.getBalance() + initialDeposit);
        }
        return account;
    }

    public ServiceResult deposit(String accountId, double amount, LocalDate today) {
        Account account = accountRepository.findById(accountId);
        if (account == null) {
            return ServiceResult.failure("Unknown account: " + accountId);
        }
        String error = validateDeposit(account, amount);
        if (error != null) {
            return ServiceResult.failure(error);
        }

        double newBalance = account.getBalance() + amount;
        recordTransaction(account, TransactionType.DEPOSIT, amount, today, newBalance);
        return ServiceResult.success("Deposit successful, new balance " + newBalance);
    }

    public ServiceResult withdraw(String accountId, double amount, LocalDate today) {
        Account account = accountRepository.findById(accountId);
        if (account == null) {
            return ServiceResult.failure("Unknown account: " + accountId);
        }
        String error = validateWithdrawal(account, amount);
        if (error != null) {
            return ServiceResult.failure(error);
        }

        double newBalance = account.getBalance() - amount;
        recordTransaction(account, TransactionType.WITHDRAWAL, amount, today, newBalance);
        return ServiceResult.success("Withdrawal successful, new balance " + newBalance);
    }

    public ServiceResult transfer(String fromAccountId, String toAccountId, double amount, LocalDate today) {
        Account fromAccount = accountRepository.findById(fromAccountId);
        if (fromAccount == null) {
            return ServiceResult.failure("Unknown account: " + fromAccountId);
        }
        Account toAccount = accountRepository.findById(toAccountId);
        if (toAccount == null) {
            return ServiceResult.failure("Unknown account: " + toAccountId);
        }

        // Validate both legs before mutating either account, so a transfer
        // either fully happens or leaves no trace at all.
        String withdrawalError = validateWithdrawal(fromAccount, amount);
        if (withdrawalError != null) {
            return ServiceResult.failure(withdrawalError);
        }
        String depositError = validateDeposit(toAccount, amount);
        if (depositError != null) {
            return ServiceResult.failure(depositError);
        }

        withdraw(fromAccountId, amount, today);
        deposit(toAccountId, amount, today);
        return ServiceResult.success("Transfer complete");
    }

    private String validateDeposit(Account account, double amount) {
        if (amount <= 0) {
            return "Deposit amount must be positive";
        }
        if (account.getStatus() == AccountStatus.FROZEN) {
            return "Account is frozen";
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            return "Account is closed";
        }
        return null;
    }

    private String validateWithdrawal(Account account, double amount) {
        if (amount <= 0) {
            return "Withdrawal amount must be positive";
        }
        if (account.getStatus() == AccountStatus.FROZEN || account.getStatus() == AccountStatus.CLOSED) {
            return "Withdrawals are not permitted on this account right now";
        }

        double newBalance = account.getBalance() - amount;
        if (account.getType() == AccountType.CHECKING) {
            if (newBalance < -CHECKING_OVERDRAFT_LIMIT) {
                return "Overdraft limit exceeded";
            }
        } else {
            if (newBalance < 0) {
                return "Insufficient funds";
            }
        }
        return null;
    }

    public ServiceResult freeze(String accountId) {
        Account account = accountRepository.findById(accountId);
        if (account == null) {
            return ServiceResult.failure("Unknown account: " + accountId);
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            return ServiceResult.failure("Cannot freeze a closed account");
        }
        account.setStatus(AccountStatus.FROZEN);
        return ServiceResult.success("Account frozen");
    }

    public ServiceResult unfreeze(String accountId) {
        Account account = accountRepository.findById(accountId);
        if (account == null) {
            return ServiceResult.failure("Unknown account: " + accountId);
        }
        if (account.getStatus() != AccountStatus.FROZEN) {
            return ServiceResult.failure("Account is not frozen");
        }
        account.setStatus(AccountStatus.ACTIVE);
        return ServiceResult.success("Account reactivated");
    }

    public ServiceResult closeAccount(String accountId) {
        Account account = accountRepository.findById(accountId);
        if (account == null) {
            return ServiceResult.failure("Unknown account: " + accountId);
        }
        account.setStatus(AccountStatus.CLOSED);
        return ServiceResult.success("Account closed");
    }

    public List<String> applyMonthlyInterestToAllSavings(List<Account> accounts, LocalDate today) {
        List<String> errors = new ArrayList<>();
        for (Account account : accounts) {
            try {
                if (account.getType() != AccountType.SAVINGS) {
                    continue;
                }
                if (account.getStatus() == AccountStatus.CLOSED) {
                    throw new IllegalStateException("Cannot apply interest to closed account: " + account.getId());
                }
                double interest = computeMonthlyInterest(account.getBalance());
                double newBalance = account.getBalance() + interest;
                recordTransaction(account, TransactionType.INTEREST, interest, today, newBalance);
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        }
        return errors;
    }

    private double computeMonthlyInterest(double balance) {
        return Math.round(balance * MONTHLY_INTEREST_RATE * 100) / 100.0;
    }

    public List<Transaction> getStatement(String accountId, LocalDate from, LocalDate to) {
        Account account = accountRepository.findById(accountId);
        if (account == null) {
            return new ArrayList<>();
        }
        List<Transaction> results = new ArrayList<>();
        for (Transaction t : account.getTransactions()) {
            if (!t.getTimestamp().isAfter(to) && !t.getTimestamp().isBefore(from)) {
                results.add(t);
            }
        }
        return results;
    }

    public List<Transaction> getTransactionHistorySorted(String accountId) {
        Account account = accountRepository.findById(accountId);
        if (account == null) {
            return new ArrayList<>();
        }
        List<Transaction> history = new ArrayList<>(account.getTransactions());
        history.sort(Comparator.comparing(Transaction::getTimestamp).reversed());
        return history;
    }

    public Account findAccount(String accountId) {
        return accountRepository.findById(accountId);
    }

    private void recordTransaction(Account account, TransactionType type, double amount, LocalDate today, double newBalance) {
        String txnId = "txn-" + (++transactionCounter);
        Transaction txn = new Transaction(txnId, account.getId(), type, amount, today, newBalance);
        account.addTransaction(txn);
    }

    public static class ServiceResult {
        private final boolean success;
        private final String message;

        private ServiceResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ServiceResult success(String message) {
            return new ServiceResult(true, message);
        }

        public static ServiceResult failure(String message) {
            return new ServiceResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
