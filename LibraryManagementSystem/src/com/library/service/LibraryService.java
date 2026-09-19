package com.library.service;

import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.repository.BookRepository;
import com.library.repository.MemberRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class LibraryService {

    private static final int MAX_ACTIVE_LOANS = 3;
    private static final int LOAN_PERIOD_DAYS = 14;
    private static final double LATE_FEE_PER_DAY = 0.50;

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;

    public LibraryService(BookRepository bookRepository, MemberRepository memberRepository) {
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
    }

    public void addBook(String isbn, String title, String author, int copies) {
        bookRepository.save(new Book(isbn, title, author, copies));
    }

    public void registerMember(String memberId, String name) {
        memberRepository.save(new Member(memberId, name));
    }

    public List<Book> searchBooks(String query) {
        return bookRepository.searchByTitle(query);
    }

    public Book findBook(String isbn) {
        return bookRepository.findByIsbn(isbn);
    }

    public Member findMember(String memberId) {
        return memberRepository.findById(memberId);
    }

    public ServiceResult borrowBook(String memberId, String isbn, LocalDate today) {
        Member member = memberRepository.findById(memberId);
        if (member == null) {
            return ServiceResult.failure("Unknown member: " + memberId);
        }

        Book book = bookRepository.findByIsbn(isbn);
        if (book == null) {
            return ServiceResult.failure("Unknown book: " + isbn);
        }

        if (book.getAvailableCopies() <= 0) {
            return ServiceResult.failure("No available copies of: " + isbn);
        }

        List<Loan> activeLoans = member.getActiveLoans();
        if (activeLoans.size() >= MAX_ACTIVE_LOANS) {
            return ServiceResult.failure("Member has reached the maximum number of active loans");
        }

        for (Loan loan : activeLoans) {
            if (loan.getIsbn().equals(isbn)) {
                return ServiceResult.failure("Member already has an active loan for this book");
            }
        }

        LocalDate dueDate = today.plusDays(LOAN_PERIOD_DAYS);
        Loan loan = new Loan(isbn, memberId, today, dueDate);
        member.addLoan(loan);
        book.decrementAvailable();

        return ServiceResult.success("Borrowed successfully, due " + dueDate);
    }

    public ServiceResult returnBook(String memberId, String isbn, LocalDate today) {
        Member member = memberRepository.findById(memberId);
        if (member == null) {
            return ServiceResult.failure("Unknown member: " + memberId);
        }

        Loan activeLoan = findActiveLoan(member, isbn);
        if (activeLoan == null) {
            return ServiceResult.failure("No active loan found for this book: " + isbn);
        }

        long daysLate = ChronoUnit.DAYS.between(activeLoan.getDueDate(), today);
        double fee = 0.0;
        if (daysLate > 0) {
            fee = daysLate * LATE_FEE_PER_DAY;
        }

        activeLoan.markReturned(today);

        Book book = bookRepository.findByIsbn(isbn);
        if (book != null) {
            book.incrementAvailable();
        }

        return ServiceResult.success(String.format("Returned. Late fee: $%.2f", fee));
    }

    private Loan findActiveLoan(Member member, String isbn) {
        for (Loan loan : member.getActiveLoans()) {
            if (loan.getIsbn().equals(isbn)) {
                return loan;
            }
        }
        return null;
    }

    public List<Loan> getMemberHistory(String memberId) {
        Member member = memberRepository.findById(memberId);
        if (member == null) {
            return new ArrayList<>();
        }
        return member.getLoanHistory();
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
