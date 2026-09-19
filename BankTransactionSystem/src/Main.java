import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.repository.AccountRepository;
import com.bank.service.BankService;
import com.bank.service.BankService.ServiceResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo/test runner for the Bank Account & Transaction System.
 *
 * Exercises the acceptance criteria from PRD.md section 6.
 * Each scenario prints PASS or FAIL (or CRASHED if it throws an
 * unexpected exception). A FAIL or CRASHED means there is a bug to
 * find in the corresponding service/model code.
 */
public class Main {

    private static int passCount = 0;
    private static int failCount = 0;
    private static int accountCounter = 0;

    public static void main(String[] args) {
        System.out.println("=== Bank Account & Transaction System - Scenario Runner ===\n");

        scenario1_openAccountRecordsInitialDeposit();
        scenario2_depositIntoActiveSucceeds();
        scenario3_depositIntoFrozenRejected();
        scenario4_depositIntoClosedRejected();
        scenario5_checkingOverdraftBoundaryAllowed();
        scenario6_checkingOverdraftBoundaryExceeded();
        scenario7_savingsCannotGoNegative();
        scenario8_smallWithdrawalOnClosedRejected();
        scenario9_largeWithdrawalOnClosedRejected();
        scenario10_transferToFrozenTargetRollsBack();
        scenario11_interestBatchReportsClosedAccountError();
        scenario12_interestRoundsToNearestCent();
        scenario13_historySortedByDateNotAmount();
        scenario14_statementIncludesBoundaryDates();
        scenario15_accountsDoNotShareHistory();

        System.out.println("\n=== Summary: " + passCount + " passed, " + failCount + " failed ===");
    }

    private static void scenario1_openAccountRecordsInitialDeposit() {
        run("Scenario 1: Opening an account records the initial deposit", () -> {
            BankService service = newService();
            Account account = service.openAccount(nextId(), "Alice", AccountType.CHECKING, 100.0, LocalDate.of(2026, 1, 1));

            check(account.getBalance() == 100.0, "expected balance 100.0, got " + account.getBalance());
            check(account.getTransactions().size() == 1, "expected 1 transaction, got " + account.getTransactions().size());
        });
    }

    private static void scenario2_depositIntoActiveSucceeds() {
        run("Scenario 2: Deposit into an ACTIVE account succeeds", () -> {
            BankService service = newService();
            String id = nextId();
            service.openAccount(id, "Alice", AccountType.CHECKING, 100.0, LocalDate.of(2026, 1, 1));

            ServiceResult result = service.deposit(id, 50.0, LocalDate.of(2026, 1, 2));

            check(result.isSuccess(), "deposit should succeed");
            check(service.findAccount(id).getBalance() == 150.0, "expected balance 150.0, got " + service.findAccount(id).getBalance());
        });
    }

    private static void scenario3_depositIntoFrozenRejected() {
        run("Scenario 3: Deposit into a FROZEN account is rejected", () -> {
            BankService service = newService();
            String id = nextId();
            service.openAccount(id, "Alice", AccountType.CHECKING, 100.0, LocalDate.of(2026, 1, 1));
            service.freeze(id);

            ServiceResult result = service.deposit(id, 50.0, LocalDate.of(2026, 1, 2));

            check(!result.isSuccess(), "deposit into a frozen account should be rejected");
        });
    }

    private static void scenario4_depositIntoClosedRejected() {
        run("Scenario 4: Deposit into a CLOSED account is rejected, even for $1", () -> {
            BankService service = newService();
            String id = nextId();
            service.openAccount(id, "Alice", AccountType.CHECKING, 100.0, LocalDate.of(2026, 1, 1));
            service.closeAccount(id);

            ServiceResult result = service.deposit(id, 1.0, LocalDate.of(2026, 1, 2));

            check(!result.isSuccess(), "deposit into a closed account should be rejected, but got: " + result.getMessage());
        });
    }

    private static void scenario5_checkingOverdraftBoundaryAllowed() {
        run("Scenario 5: CHECKING withdrawal to exactly -$500 succeeds", () -> {
            BankService service = newService();
            String id = nextId();
            service.openAccount(id, "Alice", AccountType.CHECKING, 0.0, LocalDate.of(2026, 1, 1));

            ServiceResult result = service.withdraw(id, 500.0, LocalDate.of(2026, 1, 2));

            check(result.isSuccess(), "withdrawal to exactly -$500 should be allowed, but got: " + result.getMessage());
            check(service.findAccount(id).getBalance() == -500.0, "expected balance -500.0, got " + service.findAccount(id).getBalance());
        });
    }

    private static void scenario6_checkingOverdraftBoundaryExceeded() {
        run("Scenario 6: CHECKING withdrawal beyond -$500 is rejected", () -> {
            BankService service = newService();
            String id = nextId();
            service.openAccount(id, "Alice", AccountType.CHECKING, 0.0, LocalDate.of(2026, 1, 1));

            ServiceResult result = service.withdraw(id, 500.01, LocalDate.of(2026, 1, 2));

            check(!result.isSuccess(), "withdrawal beyond -$500 should be rejected");
        });
    }

    private static void scenario7_savingsCannotGoNegative() {
        run("Scenario 7: SAVINGS withdrawal that would go negative is rejected", () -> {
            BankService service = newService();
            String id = nextId();
            service.openAccount(id, "Alice", AccountType.SAVINGS, 100.0, LocalDate.of(2026, 1, 1));

            ServiceResult result = service.withdraw(id, 100.01, LocalDate.of(2026, 1, 2));

            check(!result.isSuccess(), "savings withdrawal going negative should be rejected");
        });
    }

    private static void scenario8_smallWithdrawalOnClosedRejected() {
        run("Scenario 8: Small withdrawal on a CLOSED account is rejected", () -> {
            BankService service = newService();
            String id = nextId();
            service.openAccount(id, "Alice", AccountType.CHECKING, 100.0, LocalDate.of(2026, 1, 1));
            service.closeAccount(id);

            ServiceResult result = service.withdraw(id, 10.0, LocalDate.of(2026, 1, 2));

            check(!result.isSuccess(), "any withdrawal on a closed account should be rejected, but got: " + result.getMessage());
        });
    }

    private static void scenario9_largeWithdrawalOnClosedRejected() {
        run("Scenario 9: Large withdrawal on a CLOSED account is rejected", () -> {
            BankService service = newService();
            String id = nextId();
            service.openAccount(id, "Alice", AccountType.CHECKING, 5000.0, LocalDate.of(2026, 1, 1));
            service.closeAccount(id);

            ServiceResult result = service.withdraw(id, 2000.0, LocalDate.of(2026, 1, 2));

            check(!result.isSuccess(), "large withdrawal on a closed account should be rejected");
        });
    }

    private static void scenario10_transferToFrozenTargetRollsBack() {
        run("Scenario 10: Transfer to a FROZEN target rolls back the source withdrawal", () -> {
            BankService service = newService();
            String fromId = nextId();
            String toId = nextId();
            service.openAccount(fromId, "Alice", AccountType.CHECKING, 500.0, LocalDate.of(2026, 1, 1));
            service.openAccount(toId, "Bob", AccountType.CHECKING, 0.0, LocalDate.of(2026, 1, 1));
            service.freeze(toId);

            int transactionsBefore = service.findAccount(fromId).getTransactions().size();
            ServiceResult result = service.transfer(fromId, toId, 200.0, LocalDate.of(2026, 1, 2));

            check(!result.isSuccess(), "transfer to a frozen account should fail");
            check(service.findAccount(fromId).getBalance() == 500.0,
                    "source balance should be unchanged (500.0), got " + service.findAccount(fromId).getBalance());
            check(service.findAccount(fromId).getTransactions().size() == transactionsBefore,
                    "source transaction count should be unchanged, was " + transactionsBefore
                            + ", now " + service.findAccount(fromId).getTransactions().size());
        });
    }

    private static void scenario11_interestBatchReportsClosedAccountError() {
        run("Scenario 11: Interest batch reports an error for a closed account", () -> {
            BankService service = newService();
            String activeId = nextId();
            String closedId = nextId();
            service.openAccount(activeId, "Alice", AccountType.SAVINGS, 1000.0, LocalDate.of(2026, 1, 1));
            service.openAccount(closedId, "Bob", AccountType.SAVINGS, 1000.0, LocalDate.of(2026, 1, 1));
            service.closeAccount(closedId);

            List<Account> accounts = new ArrayList<>();
            accounts.add(service.findAccount(activeId));
            accounts.add(service.findAccount(closedId));

            List<String> errors = service.applyMonthlyInterestToAllSavings(accounts, LocalDate.of(2026, 2, 1));

            check(errors.size() == 1, "expected exactly 1 error reported for the closed account, got " + errors.size());
        });
    }

    private static void scenario12_interestRoundsToNearestCent() {
        run("Scenario 12: Interest rounds to the nearest cent (round-half-up)", () -> {
            BankService service = newService();
            String id = nextId();
            service.openAccount(id, "Alice", AccountType.SAVINGS, 999.30, LocalDate.of(2026, 1, 1));

            List<Account> accounts = new ArrayList<>();
            accounts.add(service.findAccount(id));
            service.applyMonthlyInterestToAllSavings(accounts, LocalDate.of(2026, 2, 1));

            // 999.30 * 0.5% = 4.9965 -> rounds to 5.00 (not truncated to 4.99)
            double balance = service.findAccount(id).getBalance();
            double expected = 1004.30;
            check(Math.abs(balance - expected) < 0.001, "expected balance " + expected + " after interest, got " + balance);
        });
    }

    private static void scenario13_historySortedByDateNotAmount() {
        run("Scenario 13: Transaction history is sorted by date, not amount", () -> {
            BankService service = newService();
            String id = nextId();
            service.openAccount(id, "Alice", AccountType.CHECKING, 200.0, LocalDate.of(2026, 1, 1));
            service.deposit(id, 10.0, LocalDate.of(2026, 1, 2));
            service.deposit(id, 500.0, LocalDate.of(2026, 1, 3));

            List<Transaction> sorted = service.getTransactionHistorySorted(id);

            check(sorted.size() == 3, "expected 3 transactions, got " + sorted.size());
            if (sorted.size() == 3) {
                check(sorted.get(0).getTimestamp().equals(LocalDate.of(2026, 1, 3)), "expected most recent (Jan 3) first, got " + sorted.get(0).getTimestamp());
                check(sorted.get(1).getTimestamp().equals(LocalDate.of(2026, 1, 2)), "expected Jan 2 second, got " + sorted.get(1).getTimestamp());
                check(sorted.get(2).getTimestamp().equals(LocalDate.of(2026, 1, 1)), "expected Jan 1 last, got " + sorted.get(2).getTimestamp());
            }
        });
    }

    private static void scenario14_statementIncludesBoundaryDates() {
        run("Scenario 14: Statement includes transactions on the exact boundary dates", () -> {
            BankService service = newService();
            String id = nextId();
            LocalDate day1 = LocalDate.of(2026, 1, 1);
            LocalDate day2 = LocalDate.of(2026, 1, 2);
            LocalDate day3 = LocalDate.of(2026, 1, 3);

            service.openAccount(id, "Alice", AccountType.CHECKING, 200.0, day1);
            service.deposit(id, 10.0, day2);
            service.deposit(id, 20.0, day3);

            List<Transaction> statement = service.getStatement(id, day1, day3);

            check(statement.size() == 3, "expected all 3 transactions (inclusive range), got " + statement.size());
        });
    }

    private static void scenario15_accountsDoNotShareHistory() {
        run("Scenario 15: Two accounts never share transaction history", () -> {
            BankService service = newService();
            String idA = nextId();
            String idB = nextId();
            service.openAccount(idA, "Alice", AccountType.CHECKING, 100.0, LocalDate.of(2026, 1, 1));
            service.openAccount(idB, "Bob", AccountType.CHECKING, 50.0, LocalDate.of(2026, 1, 1));

            service.deposit(idA, 999.0, LocalDate.of(2026, 1, 2));

            List<Transaction> bHistory = service.findAccount(idB).getTransactions();
            for (Transaction t : bHistory) {
                check(t.getAccountId().equals(idB), "Bob's history contains a transaction belonging to " + t.getAccountId());
            }
            check(bHistory.size() == 1, "expected Bob to have exactly 1 transaction, got " + bHistory.size());
        });
    }

    // ---- test harness helpers ----

    private static BankService newService() {
        return new BankService(new AccountRepository());
    }

    private static String nextId() {
        return "acct-" + (++accountCounter);
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
