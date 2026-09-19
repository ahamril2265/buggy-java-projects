package com.library.model;

import java.time.LocalDate;

public class Loan {
    private final String isbn;
    private final String memberId;
    private final LocalDate borrowedDate;
    private final LocalDate dueDate;
    private LocalDate returnedDate;

    public Loan(String isbn, String memberId, LocalDate borrowedDate, LocalDate dueDate) {
        this.isbn = isbn;
        this.memberId = memberId;
        this.borrowedDate = borrowedDate;
        this.dueDate = dueDate;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getMemberId() {
        return memberId;
    }

    public LocalDate getBorrowedDate() {
        return borrowedDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReturnedDate() {
        return returnedDate;
    }

    public boolean isReturned() {
        return returnedDate != null;
    }

    public void markReturned(LocalDate returnedDate) {
        this.returnedDate = returnedDate;
    }

    @Override
    public String toString() {
        return String.format("Loan{isbn=%s, memberId=%s, borrowed=%s, due=%s, returned=%s}",
                isbn, memberId, borrowedDate, dueDate, returnedDate);
    }
}
