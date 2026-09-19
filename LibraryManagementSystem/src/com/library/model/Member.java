package com.library.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class Member {
    private final String memberId;
    private final String name;
    private final List<Loan> loanHistory = new ArrayList<>();

    public Member(String memberId, String name) {
        this.memberId = memberId;
        this.name = name;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getName() {
        return name;
    }

    public void addLoan(Loan loan) {
        loanHistory.add(loan);
    }

    public List<Loan> getLoanHistory() {
        return new ArrayList<>(loanHistory);
    }

    public List<Loan> getActiveLoans() {
        List<Loan> active = new ArrayList<>();
        for (Loan loan : loanHistory) {
            if (!loan.isReturned()) {
                active.add(loan);
            }
        }
        return active;
    }

    @Override
    public String toString() {
        return String.format("Member{id=%s, name=%s, activeLoans=%d}",
                memberId, name, getActiveLoans().size());
    }
}
