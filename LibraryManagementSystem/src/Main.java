import com.library.model.Book;
import com.library.model.Loan;
import com.library.repository.BookRepository;
import com.library.repository.MemberRepository;
import com.library.service.LibraryService;
import com.library.service.LibraryService.ServiceResult;

import java.time.LocalDate;
import java.util.List;

/**
 * Demo/test runner for the Library Management System.
 *
 * This exercises the acceptance criteria from PRD.md section 6.
 * Each scenario prints PASS or FAIL (or CRASHED if it throws an
 * unexpected exception). A FAIL or CRASHED means there is a bug
 * to find in the corresponding service/repository/model code.
 */
public class Main {

    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) {
        System.out.println("=== Library Management System - Scenario Runner ===\n");

        scenario1_borrowDecrementsAvailability();
        scenario2_borrowWithNoCopiesRejected();
        scenario3_fourthSimultaneousLoanRejected();
        scenario4_duplicateLoanRejected();
        scenario5_onTimeReturnNoFee();
        scenario6_threeDaysLateFee();
        scenario7_emptySearchReturnsEmptyList();
        scenario8_reportIsolationFromCatalogMutation();
        scenario9_returnRestoresAvailableCopies();
        scenario10_returningUnborrowedBookFailsCleanly();

        System.out.println("\n=== Summary: " + passCount + " passed, " + failCount + " failed ===");
    }

    // Scenario 1: Borrowing an available book succeeds and decrements availableCopies.
    private static void scenario1_borrowDecrementsAvailability() {
        run("Scenario 1: Borrow decrements availableCopies", () -> {
            LibraryService service = newService();
            service.addBook("111", "Clean Code", "Robert Martin", 2);
            service.registerMember("m1", "Alice");

            ServiceResult result = service.borrowBook("m1", "111", LocalDate.of(2026, 1, 1));
            Book book = service.findBook("111");

            check(result.isSuccess(), "borrow should succeed");
            check(book.getAvailableCopies() == 1, "expected 1 available copy, got " + book.getAvailableCopies());
        });
    }

    // Scenario 2: Borrowing with 0 available copies is rejected.
    private static void scenario2_borrowWithNoCopiesRejected() {
        run("Scenario 2: Borrow with 0 copies is rejected", () -> {
            LibraryService service = newService();
            service.addBook("111", "Clean Code", "Robert Martin", 1);
            service.registerMember("m1", "Alice");
            service.registerMember("m2", "Bob");

            service.borrowBook("m1", "111", LocalDate.of(2026, 1, 1));
            ServiceResult result = service.borrowBook("m2", "111", LocalDate.of(2026, 1, 1));

            check(!result.isSuccess(), "second borrow should be rejected (no copies left)");
        });
    }

    // Scenario 3: A member attempting a 4th simultaneous loan is rejected (max 3).
    private static void scenario3_fourthSimultaneousLoanRejected() {
        run("Scenario 3: 4th simultaneous loan is rejected", () -> {
            LibraryService service = newService();
            service.registerMember("m1", "Alice");
            service.addBook("b1", "Book One", "Author A", 5);
            service.addBook("b2", "Book Two", "Author B", 5);
            service.addBook("b3", "Book Three", "Author C", 5);
            service.addBook("b4", "Book Four", "Author D", 5);

            LocalDate day = LocalDate.of(2026, 1, 1);
            service.borrowBook("m1", "b1", day);
            service.borrowBook("m1", "b2", day);
            service.borrowBook("m1", "b3", day);
            ServiceResult fourth = service.borrowBook("m1", "b4", day);

            check(!fourth.isSuccess(), "4th simultaneous loan should be rejected, but got: " + fourth.getMessage());
        });
    }

    // Scenario 4: A member cannot hold two active loans for the same ISBN.
    private static void scenario4_duplicateLoanRejected() {
        run("Scenario 4: Duplicate active loan for same book is rejected", () -> {
            LibraryService service = newService();
            service.registerMember("m1", "Alice");
            service.addBook("111", "Clean Code", "Robert Martin", 3);

            LocalDate day = LocalDate.of(2026, 1, 1);
            service.borrowBook("m1", "111", day);
            ServiceResult second = service.borrowBook("m1", "111", day);

            check(!second.isSuccess(), "duplicate borrow of same book should be rejected");
        });
    }

    // Scenario 5: Returning a book exactly on the 14-day due date incurs no fee.
    private static void scenario5_onTimeReturnNoFee() {
        run("Scenario 5: On-time return (day 14) incurs no fee", () -> {
            LibraryService service = newService();
            service.registerMember("m1", "Alice");
            service.addBook("111", "Clean Code", "Robert Martin", 3);

            LocalDate borrowDay = LocalDate.of(2026, 1, 1);
            service.borrowBook("m1", "111", borrowDay);

            LocalDate dueDay = borrowDay.plusDays(14);
            ServiceResult result = service.returnBook("m1", "111", dueDay);

            check(result.isSuccess(), "return should succeed");
            check(result.getMessage().contains("$0.00"), "expected $0.00 fee, got: " + result.getMessage());
        });
    }

    // Scenario 6: Returning a book 3 days late incurs a $1.50 fee.
    private static void scenario6_threeDaysLateFee() {
        run("Scenario 6: Returning 3 days late incurs $1.50 fee", () -> {
            LibraryService service = newService();
            service.registerMember("m1", "Alice");
            service.addBook("111", "Clean Code", "Robert Martin", 3);

            LocalDate borrowDay = LocalDate.of(2026, 1, 1);
            service.borrowBook("m1", "111", borrowDay);

            LocalDate lateReturnDay = borrowDay.plusDays(17); // 3 days after the 14-day due date
            ServiceResult result = service.returnBook("m1", "111", lateReturnDay);

            check(result.isSuccess(), "return should succeed");
            check(result.getMessage().contains("$1.50"), "expected $1.50 fee, got: " + result.getMessage());
        });
    }

    // Scenario 7: Searching with an empty string returns an empty list, not an error.
    private static void scenario7_emptySearchReturnsEmptyList() {
        run("Scenario 7: Empty search query returns empty list", () -> {
            LibraryService service = newService();
            service.addBook("111", "Clean Code", "Robert Martin", 3);

            List<Book> results = service.searchBooks("");

            check(results != null, "results should not be null");
            check(results.isEmpty(), "expected empty results, got " + results.size());
        });
    }

    // Scenario 8: Mutating a returned report (member's loan history) must not corrupt real state.
    private static void scenario8_reportIsolationFromCatalogMutation() {
        run("Scenario 8: Mutating returned loan history does not corrupt real state", () -> {
            LibraryService service = newService();
            service.registerMember("m1", "Alice");
            service.addBook("111", "Clean Code", "Robert Martin", 3);

            LocalDate day = LocalDate.of(2026, 1, 1);
            service.borrowBook("m1", "111", day);

            List<Loan> history = service.getMemberHistory("m1");
            history.clear(); // caller mutates the list they were handed

            List<Loan> historyAgain = service.getMemberHistory("m1");
            check(historyAgain.size() == 1, "expected member history to still have 1 loan, got " + historyAgain.size());
        });
    }

    // Scenario 9: Returning a book restores availableCopies to its prior count.
    private static void scenario9_returnRestoresAvailableCopies() {
        run("Scenario 9: Returning a book restores availableCopies", () -> {
            LibraryService service = newService();
            service.registerMember("m1", "Alice");
            service.addBook("111", "Clean Code", "Robert Martin", 3);

            LocalDate day = LocalDate.of(2026, 1, 1);
            service.borrowBook("m1", "111", day);
            Book afterBorrow = service.findBook("111");
            check(afterBorrow.getAvailableCopies() == 2, "expected 2 available after borrow, got " + afterBorrow.getAvailableCopies());

            service.returnBook("m1", "111", day.plusDays(5));
            Book afterReturn = service.findBook("111");
            check(afterReturn.getAvailableCopies() == 3, "expected 3 available after return, got " + afterReturn.getAvailableCopies());
        });
    }

    // Scenario 10: Returning a book the member never borrowed fails cleanly (no crash).
    private static void scenario10_returningUnborrowedBookFailsCleanly() {
        run("Scenario 10: Returning an unborrowed book fails cleanly", () -> {
            LibraryService service = newService();
            service.registerMember("m1", "Alice");
            service.addBook("111", "Clean Code", "Robert Martin", 3);

            ServiceResult result = service.returnBook("m1", "111", LocalDate.of(2026, 1, 1));

            check(!result.isSuccess(), "returning a book never borrowed should fail cleanly, not crash");
        });
    }

    // ---- test harness helpers ----

    private static LibraryService newService() {
        return new LibraryService(new BookRepository(), new MemberRepository());
    }

    private interface ScenarioBody {
        void run();
    }

    private static void run(String name, ScenarioBody body) {
        try {
            scenarioFailedFlag = false;
            body.run();
            if (scenarioFailedFlag) {
                failCount++;
                System.out.println("[FAIL] " + name);
            } else {
                passCount++;
                System.out.println("[PASS] " + name);
            }
        } catch (Exception e) {
            failCount++;
            System.out.println("[CRASHED] " + name + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static boolean scenarioFailedFlag = false;

    private static void check(boolean condition, String failureMessage) {
        if (!condition) {
            scenarioFailedFlag = true;
            System.out.println("        assertion failed: " + failureMessage);
        }
    }
}
