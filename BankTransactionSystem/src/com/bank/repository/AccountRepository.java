package com.bank.repository;

import com.bank.model.Account;

import java.util.LinkedHashMap;
import java.util.Map;

public class AccountRepository {
    private final Map<String, Account> accountsById = new LinkedHashMap<>();

    public void save(Account account) {
        accountsById.put(account.getId(), account);
    }

    public Account findById(String accountId) {
        if (accountId == null) {
            return null;
        }
        return accountsById.get(accountId);
    }
}
