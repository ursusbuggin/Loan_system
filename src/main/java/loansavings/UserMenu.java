package loansavings;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Menu for a logged-in member. */
final class UserMenu {
    static final double BASIC_LIMIT = 50_000, PREMIUM_LIMIT = 500_000;
    static final double BASIC_RATE = 12.0, PREMIUM_RATE = 10.0;
    static final int MAX_TERM = 36;

    private final Connection c;
    private final long uid;
    private final String username;

    UserMenu(Connection c, long uid, String username) {
        this.c = c;
        this.uid = uid;
        this.username = username;
    }

    void run() {
        while (true) {
            final int choice = Cli.menu("MEMBER MENU - " + username,
                    "My profile", "Apply for a loan", "My loans", "Payment schedule and billing history",
                    "Pay next installment", "Savings balance and history", "Deposit to savings",
                    "Request a withdrawal", "My withdrawal requests", "Money back credits");
            if (choice == 0) return;
            Cli.safe(() -> {
                switch (choice) {
                    case 1 -> profile();
                    case 2 -> applyLoan();
                    case 3 -> myLoans();
                    case 4 -> schedule();
                    case 5 -> payNext();
                    case 6 -> savingsHistory();
                    case 7 -> deposit();
                    case 8 -> requestWithdrawal();
                    case 9 -> myWithdrawals();
                    case 10 -> moneyBack();
                    default -> { }
                }
            });
            Cli.pause();
        }
    }

    private Row savingsAccount() throws SQLException {
        Row s = Db.one(c, "SELECT savings_id, balance FROM savings_accounts WHERE user_id = ?", uid);
        if (s == null) System.out.println("You do not have a savings account yet.");
        return s;
    }

    private void profile() throws SQLException {
        Row r = Db.one(c, """
                SELECT u.username, u.email, u.account_type, u.account_status,
                       p.first_name || ' ' || p.last_name AS full_name, p.birth_date, p.phone, p.address,
                       b.bank_name, b.account_number
                FROM users u
                JOIN user_profiles p ON p.user_id = u.user_id
                LEFT JOIN bank_details b ON b.user_id = u.user_id AND b.is_primary = 1
                WHERE u.user_id = ?""", uid);
        Cli.title("MY PROFILE");
        System.out.println("Name         : " + r.str("full_name"));
        System.out.println("Username     : " + r.str("username"));
        System.out.println("Email        : " + r.str("email"));
        System.out.println("Account type : " + r.str("account_type") + " (" + r.str("account_status") + ")");
        System.out.println("Birth date   : " + r.str("birth_date"));
        System.out.println("Phone        : " + r.str("phone"));
        System.out.println("Address      : " + r.str("address"));
        System.out.println("Bank         : " + r.str("bank_name") + " " + r.str("account_number"));
    }

    private void applyLoan() throws SQLException {
        Row u = Db.one(c, "SELECT account_type FROM users WHERE user_id = ?", uid);
        boolean premium = u.str("account_type").equals("Premium");
        double limit = premium ? PREMIUM_LIMIT : BASIC_LIMIT, rate = premium ? PREMIUM_RATE : BASIC_RATE;
        if (Db.one(c, "SELECT 1 AS x FROM loan_applications WHERE user_id = ? AND status = 'Pending'", uid) != null) {
            System.out.println("You already have a pending loan application.");
            return;
        }
        Cli.title("LOAN APPLICATION");
        System.out.printf("%s account: maximum loan %s, interest %.2f%% per year, up to %d months.%n",
                u.str("account_type"), Cli.money(limit), rate, MAX_TERM);
        double amount = Cli.askMoney("Loan amount", 1000, limit);
        int term = Cli.askInt("Repayment period (months)", 1, MAX_TERM);
        String purpose = Cli.ask("Purpose");
        double monthly = Db.round2(amount / term + amount * rate / 100 / 12);
        System.out.println("Estimated monthly payment: " + Cli.money(monthly));
        if (!Cli.confirm("Submit application")) return;
        long id = Db.insert(c, "INSERT INTO loan_applications (user_id, loan_amount, interest_rate, term_months, purpose) "
                + "VALUES (?, ?, ?, ?, ?)", uid, amount, rate, term, purpose);
        System.out.println("Loan application #" + id + " submitted and awaiting administrator review.");
    }

    private void myLoans() throws SQLException {
        Cli.title("MY LOANS");
        List<Row> rows = Db.query(c, """
                SELECT l.loan_id, l.loan_amount, l.interest_rate, l.term_months, l.status, l.applied_at, lr.remarks
                FROM loan_applications l LEFT JOIN loan_reviews lr ON lr.loan_id = l.loan_id
                WHERE l.user_id = ? ORDER BY l.loan_id""", uid);
        Cli.table(rows, new String[] {"Loan", "Amount", "Rate %", "Months", "Status", "Applied", "Admin remarks"},
                new String[] {"loan_id", "loan_amount", "interest_rate", "term_months", "status", "applied_at", "remarks"});
    }

    private Row pickOwnLoan(String statusFilter) throws SQLException {
        List<Row> loans = Db.query(c, "SELECT loan_id, loan_amount, status FROM loan_applications "
                + "WHERE user_id = ? AND status IN (" + statusFilter + ") ORDER BY loan_id", uid);
        if (loans.isEmpty()) {
            System.out.println("No matching loans.");
            return null;
        }
        Cli.table(loans, new String[] {"Loan", "Amount", "Status"}, new String[] {"loan_id", "loan_amount", "status"});
        long id = Cli.askInt("Loan number", 1, Integer.MAX_VALUE);
        for (Row l : loans) if (l.lng("loan_id") == id) return l;
        System.out.println("Not one of your loans.");
        return null;
    }

    private void schedule() throws SQLException {
        Row loan = pickOwnLoan("'Approved','Paid'");
        if (loan == null) return;
        long id = loan.lng("loan_id");
        Cli.title("PAYMENT SCHEDULE - LOAN #" + id);
        Cli.table(Db.query(c, """
                SELECT ps.installment_no, ps.due_date, ps.principal_due, ps.interest_due,
                       COALESCE(bs.penalty_amount, 0.0) AS penalty,
                       COALESCE(bs.total_due, ps.principal_due + ps.interest_due) AS total_due,
                       COALESCE(bs.payment_status, 'Not billed') AS status
                FROM payment_schedules ps
                LEFT JOIN billing_statements bs ON bs.schedule_id = ps.schedule_id
                WHERE ps.loan_id = ? ORDER BY ps.installment_no""", id),
                new String[] {"No.", "Due date", "Principal", "Interest", "Penalty", "Total due", "Status"},
                new String[] {"installment_no", "due_date", "principal_due", "interest_due", "penalty", "total_due", "status"});
        System.out.println("\nPayment history:");
        Cli.table(Db.query(c, """
                SELECT lp.payment_date, lp.amount_paid, lp.payment_method, lt.reference_no
                FROM loan_payments lp
                JOIN billing_statements bs ON bs.statement_id = lp.statement_id
                JOIN payment_schedules ps ON ps.schedule_id = bs.schedule_id
                JOIN loan_transactions lt ON lt.loan_txn_id = lp.loan_txn_id
                WHERE ps.loan_id = ? ORDER BY lp.payment_date, lp.payment_id""", id),
                new String[] {"Date", "Amount", "Method", "Reference"},
                new String[] {"payment_date", "amount_paid", "payment_method", "reference_no"});
    }

    private void payNext() throws SQLException {
        Row loan = pickOwnLoan("'Approved'");
        if (loan == null) return;
        long loanId = loan.lng("loan_id");
        Row next = Db.one(c, """
                SELECT ps.schedule_id, ps.installment_no, ps.due_date
                FROM payment_schedules ps
                LEFT JOIN billing_statements bs ON bs.schedule_id = ps.schedule_id
                WHERE ps.loan_id = ? AND COALESCE(bs.payment_status, 'Unpaid') <> 'Paid'
                ORDER BY ps.installment_no LIMIT 1""", loanId);
        if (next == null) {
            System.out.println("Nothing left to pay on this loan.");
            return;
        }
        final long[] stmt = new long[1];
        Db.tx(c, () -> {
            Row st = Billing.ensureStatement(c, next.lng("schedule_id"));
            Billing.applyPenalty(c, st.lng("statement_id"));
            stmt[0] = st.lng("statement_id");
        });
        Row st = Db.one(c, "SELECT penalty_amount, total_due, payment_status FROM billing_statements WHERE statement_id = ?", stmt[0]);
        System.out.printf("Installment %d due %s: total %s (penalty %s) - %s%n", next.intVal("installment_no"),
                next.str("due_date"), Cli.money(st.dbl("total_due")), Cli.money(st.dbl("penalty_amount")),
                st.str("payment_status"));
        if (!Cli.confirm("Pay now")) return;
        String method = Cli.askChoice("Payment method", "Bank Transfer", "Cash", "E-Wallet");
        final Billing.PayResult[] result = new Billing.PayResult[1];
        Db.tx(c, () -> result[0] = Billing.pay(c, stmt[0], method));
        System.out.println("Payment of " + Cli.money(result[0].amount()) + " recorded.");
        if (result[0].loanClosed()) System.out.println("Congratulations - this loan is fully paid!");
    }

    private void savingsHistory() throws SQLException {
        Row s = savingsAccount();
        if (s == null) return;
        Cli.title("SAVINGS - balance " + Cli.money(s.dbl("balance")));
        Cli.table(Db.query(c, """
                SELECT txn_date, txn_type, amount, status, reference_no FROM savings_transactions
                WHERE savings_id = ? ORDER BY txn_date DESC, savings_txn_id DESC""", s.lng("savings_id")),
                new String[] {"Date", "Type", "Amount", "Status", "Reference"},
                new String[] {"txn_date", "txn_type", "amount", "status", "reference_no"});
    }

    private void deposit() throws SQLException {
        Row s = savingsAccount();
        if (s == null) return;
        double amount = Cli.askMoney("Deposit amount", 1, 10_000_000);
        Db.tx(c, () -> {
            Db.update(c, "INSERT INTO savings_transactions (savings_id, txn_type, amount, status, reference_no) "
                    + "VALUES (?, 'Deposit', ?, 'Completed', ?)", s.lng("savings_id"), amount, Db.newRef("STX"));
            Db.update(c, "UPDATE savings_accounts SET balance = balance + ? WHERE savings_id = ?", amount, s.lng("savings_id"));
        });
        Row after = Db.one(c, "SELECT balance FROM savings_accounts WHERE savings_id = ?", s.lng("savings_id"));
        System.out.println("Deposited. New balance: " + Cli.money(after.dbl("balance")));
    }

    private void requestWithdrawal() throws SQLException {
        Row s = savingsAccount();
        if (s == null) return;
        double pending = Db.one(c, "SELECT COALESCE(SUM(amount), 0.0) AS p FROM withdrawal_requests "
                + "WHERE savings_id = ? AND status = 'Pending'", s.lng("savings_id")).dbl("p");
        double available = Db.round2(s.dbl("balance") - pending);
        System.out.println("Balance " + Cli.money(s.dbl("balance")) + ", pending requests " + Cli.money(pending)
                + ", available " + Cli.money(available));
        if (available < 1) {
            System.out.println("Insufficient available balance.");
            return;
        }
        double amount = Cli.askMoney("Withdrawal amount", 1, available);
        long id = Db.insert(c, "INSERT INTO withdrawal_requests (savings_id, amount) VALUES (?, ?)", s.lng("savings_id"), amount);
        System.out.println("Withdrawal request #" + id + " submitted for administrator approval.");
    }

    private void myWithdrawals() throws SQLException {
        Row s = savingsAccount();
        if (s == null) return;
        Cli.title("MY WITHDRAWAL REQUESTS");
        Cli.table(Db.query(c, "SELECT request_id, amount, status, requested_at, remarks FROM withdrawal_requests "
                + "WHERE savings_id = ? ORDER BY request_id DESC", s.lng("savings_id")),
                new String[] {"Request", "Amount", "Status", "Requested", "Remarks"},
                new String[] {"request_id", "amount", "status", "requested_at", "remarks"});
    }

    private void moneyBack() throws SQLException {
        Cli.title("MONEY BACK CREDITS");
        Cli.table(Db.query(c, """
                SELECT ci.income_year, pa.savings_basis, pa.share_percentage, pa.allocated_amount
                FROM premium_allocations pa
                JOIN money_back_computations mb ON mb.computation_id = pa.computation_id
                JOIN company_income ci ON ci.income_id = mb.income_id
                WHERE pa.user_id = ? ORDER BY ci.income_year""", uid),
                new String[] {"Year", "Savings basis", "Share %", "Credited"},
                new String[] {"income_year", "savings_basis", "share_percentage", "allocated_amount"});
        if (Db.one(c, "SELECT 1 AS x FROM users WHERE user_id = ? AND account_type = 'Premium'", uid) == null)
            System.out.println("\nMoney back is for Premium members only.");
    }
}
