package loansavings;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Menu for a logged-in administrator. Every decision is written to the activity log. */
final class AdminMenu {
    private final Connection c;
    private final long adminId;
    private final String username;

    AdminMenu(Connection c, long adminId, String username) {
        this.c = c;
        this.adminId = adminId;
        this.username = username;
    }

    void run() {
        while (true) {
            final int choice = Cli.menu("ADMIN MENU - " + username,
                    "Review registrations", "Review loan applications", "Review withdrawal requests",
                    "Manage users", "Run billing cycle (statements and penalties)",
                    "Record company income and compute money back", "Reports", "Activity log");
            if (choice == 0) return;
            Cli.safe(() -> {
                switch (choice) {
                    case 1 -> reviewRegistrations();
                    case 2 -> reviewLoans();
                    case 3 -> reviewWithdrawals();
                    case 4 -> manageUsers();
                    case 5 -> billingCycle();
                    case 6 -> moneyBack();
                    case 7 -> reports();
                    case 8 -> activityLog();
                    default -> { }
                }
            });
            Cli.pause();
        }
    }

    // ---------- registrations ----------
    private void reviewRegistrations() throws SQLException {
        List<Row> rows = Db.query(c, """
                SELECT ra.application_id, u.username, p.first_name || ' ' || p.last_name AS full_name,
                       ra.requested_type, e.employer_name, e.monthly_income, ra.submitted_at
                FROM registration_applications ra
                JOIN users u ON u.user_id = ra.user_id
                JOIN user_profiles p ON p.user_id = u.user_id
                LEFT JOIN employment_info e ON e.application_id = ra.application_id
                WHERE ra.status = 'Pending' ORDER BY ra.application_id""");
        Cli.title("PENDING REGISTRATIONS");
        Cli.table(rows, new String[] {"App", "Username", "Name", "Type", "Employer", "Income", "Submitted"},
                new String[] {"application_id", "username", "full_name", "requested_type", "employer_name",
                        "monthly_income", "submitted_at"});
        if (rows.isEmpty()) return;
        long id = Cli.askInt("Application number (0 to cancel)", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        Row app = Db.one(c, "SELECT application_id, user_id, requested_type FROM registration_applications "
                + "WHERE application_id = ? AND status = 'Pending'", id);
        if (app == null) { System.out.println("Not a pending application."); return; }
        System.out.println("Documents:");
        Cli.table(Db.query(c, "SELECT document_type, file_path FROM application_documents WHERE application_id = ?", id),
                new String[] {"Type", "File"}, new String[] {"document_type", "file_path"});
        String decision = Cli.askChoice("Decision", "Approve", "Reject", "Skip");
        if (decision.equals("Skip")) return;
        String remarks = Cli.ask("Remarks");
        boolean approve = decision.equals("Approve");
        long userId = app.lng("user_id");
        Db.tx(c, () -> {
            String status = approve ? "Approved" : "Rejected";
            Db.update(c, "UPDATE registration_applications SET status = ? WHERE application_id = ?", status, id);
            Db.update(c, "INSERT INTO registration_reviews (application_id, admin_id, decision, remarks) VALUES (?, ?, ?, ?)",
                    id, adminId, status, remarks);
            if (approve) {
                Db.update(c, "UPDATE users SET account_status = 'Active', account_type = ? WHERE user_id = ?",
                        app.str("requested_type"), userId);
                if (Db.one(c, "SELECT 1 AS x FROM savings_accounts WHERE user_id = ?", userId) == null)
                    Db.update(c, "INSERT INTO savings_accounts (user_id) VALUES (?)", userId);
            }
            Db.log(c, adminId, approve ? "APPROVE_REGISTRATION" : "REJECT_REGISTRATION",
                    "registration_applications", id, remarks);
        });
        System.out.println("Application " + (approve ? "approved; account activated." : "rejected."));
    }

    // ---------- loans ----------
    private void reviewLoans() throws SQLException {
        List<Row> rows = Db.query(c, """
                SELECT l.loan_id, u.username, u.account_type, l.loan_amount, l.interest_rate, l.term_months,
                       l.purpose, e.monthly_income
                FROM loan_applications l
                JOIN users u ON u.user_id = l.user_id
                LEFT JOIN registration_applications ra ON ra.user_id = u.user_id AND ra.status = 'Approved'
                LEFT JOIN employment_info e ON e.application_id = ra.application_id
                WHERE l.status = 'Pending' ORDER BY l.loan_id""");
        Cli.title("PENDING LOAN APPLICATIONS");
        Cli.table(rows, new String[] {"Loan", "Member", "Type", "Amount", "Rate %", "Months", "Purpose", "Income"},
                new String[] {"loan_id", "username", "account_type", "loan_amount", "interest_rate", "term_months",
                        "purpose", "monthly_income"});
        if (rows.isEmpty()) return;
        long id = Cli.askInt("Loan number (0 to cancel)", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        Row loan = Db.one(c, "SELECT * FROM loan_applications WHERE loan_id = ? AND status = 'Pending'", id);
        if (loan == null) { System.out.println("Not a pending loan."); return; }
        String decision = Cli.askChoice("Decision", "Approve", "Reject", "Skip");
        if (decision.equals("Skip")) return;
        String remarks = Cli.ask("Remarks");
        if (decision.equals("Reject")) {
            Db.tx(c, () -> {
                Db.update(c, "UPDATE loan_applications SET status = 'Rejected' WHERE loan_id = ?", id);
                Db.update(c, "INSERT INTO loan_reviews (loan_id, admin_id, decision, remarks) VALUES (?, ?, 'Rejected', ?)",
                        id, adminId, remarks);
                Db.log(c, adminId, "REJECT_LOAN", "loan_applications", id, remarks);
            });
            System.out.println("Loan rejected.");
            return;
        }
        double amount = loan.dbl("loan_amount");
        int term = loan.intVal("term_months");
        double monthlyInterest = Db.round2(amount * loan.dbl("interest_rate") / 100 / 12);
        double basePrincipal = Db.round2(amount / term);
        LocalDate start = LocalDate.now();
        Db.tx(c, () -> {
            Db.update(c, "UPDATE loan_applications SET status = 'Approved' WHERE loan_id = ?", id);
            Db.update(c, "INSERT INTO loan_reviews (loan_id, admin_id, decision, remarks) VALUES (?, ?, 'Approved', ?)",
                    id, adminId, remarks);
            Db.update(c, "INSERT INTO loan_transactions (loan_id, txn_type, amount, reference_no) "
                    + "VALUES (?, 'Disbursement', ?, ?)", id, amount, Db.newRef("LTX"));
            double remaining = amount;
            for (int i = 1; i <= term; i++) {
                double principal = i < term ? basePrincipal : Db.round2(remaining); // last one absorbs rounding
                remaining -= basePrincipal;
                Db.update(c, "INSERT INTO payment_schedules (loan_id, installment_no, due_date, principal_due, interest_due) "
                        + "VALUES (?, ?, ?, ?, ?)", id, i, start.plusMonths(i).toString(), principal, monthlyInterest);
            }
            Db.log(c, adminId, "APPROVE_LOAN", "loan_applications", id, remarks);
        });
        System.out.println("Loan approved, funds disbursed and a " + term + "-month schedule generated.");
    }

    // ---------- withdrawals ----------
    private void reviewWithdrawals() throws SQLException {
        List<Row> rows = Db.query(c, """
                SELECT wr.request_id, u.username, wr.amount, sa.balance, wr.requested_at
                FROM withdrawal_requests wr
                JOIN savings_accounts sa ON sa.savings_id = wr.savings_id
                JOIN users u ON u.user_id = sa.user_id
                WHERE wr.status = 'Pending' ORDER BY wr.request_id""");
        Cli.title("PENDING WITHDRAWAL REQUESTS");
        Cli.table(rows, new String[] {"Request", "Member", "Amount", "Balance", "Requested"},
                new String[] {"request_id", "username", "amount", "balance", "requested_at"});
        if (rows.isEmpty()) return;
        long id = Cli.askInt("Request number (0 to cancel)", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        Row wr = Db.one(c, "SELECT wr.savings_id, wr.amount, sa.balance FROM withdrawal_requests wr "
                + "JOIN savings_accounts sa ON sa.savings_id = wr.savings_id "
                + "WHERE wr.request_id = ? AND wr.status = 'Pending'", id);
        if (wr == null) { System.out.println("Not a pending request."); return; }
        String decision = Cli.askChoice("Decision", "Approve", "Reject", "Skip");
        if (decision.equals("Skip")) return;
        String remarks = Cli.ask("Remarks");
        if (decision.equals("Approve") && wr.dbl("balance") < wr.dbl("amount")) {
            System.out.println("Balance is now lower than the request; it cannot be approved.");
            return;
        }
        boolean approve = decision.equals("Approve");
        Db.tx(c, () -> {
            if (approve) {
                long txn = Db.insert(c, "INSERT INTO savings_transactions (savings_id, txn_type, amount, status, reference_no) "
                        + "VALUES (?, 'Withdrawal', ?, 'Completed', ?)", wr.lng("savings_id"), wr.dbl("amount"), Db.newRef("STX"));
                Db.update(c, "UPDATE savings_accounts SET balance = balance - ? WHERE savings_id = ?",
                        wr.dbl("amount"), wr.lng("savings_id"));
                Db.update(c, "UPDATE withdrawal_requests SET status = 'Approved', admin_id = ?, "
                        + "reviewed_at = CURRENT_TIMESTAMP, remarks = ?, savings_txn_id = ? WHERE request_id = ?",
                        adminId, remarks, txn, id);
            } else {
                Db.update(c, "UPDATE withdrawal_requests SET status = 'Rejected', admin_id = ?, "
                        + "reviewed_at = CURRENT_TIMESTAMP, remarks = ? WHERE request_id = ?", adminId, remarks, id);
            }
            Db.log(c, adminId, approve ? "APPROVE_WITHDRAWAL" : "REJECT_WITHDRAWAL", "withdrawal_requests", id, remarks);
        });
        System.out.println("Withdrawal " + (approve ? "approved and balance updated." : "rejected."));
    }

    // ---------- users ----------
    private void manageUsers() throws SQLException {
        Cli.title("USERS");
        Cli.table(Db.query(c, "SELECT user_id, username, email, account_type, account_status FROM users ORDER BY user_id"),
                new String[] {"ID", "Username", "Email", "Type", "Status"},
                new String[] {"user_id", "username", "email", "account_type", "account_status"});
        long id = Cli.askInt("User ID (0 to cancel)", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        if (Db.one(c, "SELECT 1 AS x FROM users WHERE user_id = ?", id) == null) {
            System.out.println("No such user.");
            return;
        }
        String action = Cli.askChoice("Action", "Activate", "Disable", "Premium", "Basic");
        Db.tx(c, () -> {
            switch (action) {
                case "Activate" -> Db.update(c, "UPDATE users SET account_status = 'Active' WHERE user_id = ?", id);
                case "Disable" -> Db.update(c, "UPDATE users SET account_status = 'Disabled' WHERE user_id = ?", id);
                case "Premium" -> Db.update(c, "UPDATE users SET account_type = 'Premium' WHERE user_id = ?", id);
                default -> Db.update(c, "UPDATE users SET account_type = 'Basic' WHERE user_id = ?", id);
            }
            Db.log(c, adminId, "USER_" + action.toUpperCase(), "users", id, "Admin changed account");
        });
        System.out.println("User updated.");
    }

    // ---------- billing ----------
    private void billingCycle() throws SQLException {
        final int[][] result = new int[1][];
        Db.tx(c, () -> {
            result[0] = Billing.runCycle(c);
            Db.log(c, adminId, "RUN_BILLING", "billing_statements", 0,
                    result[0][0] + " statements created, " + result[0][1] + " penalised");
        });
        System.out.println("Statements created: " + result[0][0] + ", statements penalised/overdue: " + result[0][1]);
    }

    // ---------- money back ----------
    private void moneyBack() throws SQLException {
        Cli.title("ANNUAL MONEY BACK");
        int year = Cli.askInt("Income year", 2000, 2100);
        if (Db.one(c, "SELECT 1 AS x FROM company_income WHERE income_year = ?", year) != null) {
            System.out.println("Income for " + year + " has already been recorded and distributed.");
            return;
        }
        double income = Cli.askMoney("Total company income", 0, 1_000_000_000_000.0);
        double pct = Cli.askMoney("Percentage of income shared with Premium members", 0, 100);
        List<Row> members = Db.query(c, """
                SELECT u.user_id, u.username, sa.savings_id, sa.balance
                FROM users u JOIN savings_accounts sa ON sa.user_id = u.user_id
                WHERE u.account_type = 'Premium' AND u.account_status = 'Active' AND sa.balance > 0""");
        double totalBasis = 0;
        for (Row m : members) totalBasis += m.dbl("balance");
        if (totalBasis == 0) {
            System.out.println("No Premium savings to allocate to.");
            return;
        }
        double pool = Db.round2(income * pct / 100);
        final double basis = totalBasis;
        Db.tx(c, () -> {
            long incomeId = Db.insert(c, "INSERT INTO company_income (income_year, total_income, recorded_by) VALUES (?, ?, ?)",
                    year, income, adminId);
            long compId = Db.insert(c, "INSERT INTO money_back_computations (income_id, pool_percentage, pool_amount, "
                    + "total_premium_savings, computed_by) VALUES (?, ?, ?, ?, ?)", incomeId, pct, pool, basis, adminId);
            for (Row m : members) {
                double share = m.dbl("balance") / basis;
                double amount = Db.round2(pool * share);
                long txn = Db.insert(c, "INSERT INTO savings_transactions (savings_id, txn_type, amount, status, reference_no) "
                        + "VALUES (?, 'MoneyBack', ?, 'Completed', ?)", m.lng("savings_id"), Math.max(amount, 0.01), Db.newRef("STX"));
                Db.update(c, "UPDATE savings_accounts SET balance = balance + ? WHERE savings_id = ?", amount, m.lng("savings_id"));
                Db.update(c, "INSERT INTO premium_allocations (computation_id, user_id, savings_basis, share_percentage, "
                        + "allocated_amount, savings_txn_id) VALUES (?, ?, ?, ?, ?, ?)",
                        compId, m.lng("user_id"), m.dbl("balance"), Math.round(share * 1_000_000.0) / 10_000.0, amount, txn);
            }
            Db.log(c, adminId, "COMPUTE_MONEY_BACK", "money_back_computations", compId, "Year " + year);
        });
        System.out.println("Pool " + Cli.money(pool) + " distributed to " + members.size() + " Premium member(s).");
        Cli.table(Db.query(c, """
                SELECT u.username, pa.savings_basis, pa.share_percentage, pa.allocated_amount
                FROM premium_allocations pa
                JOIN money_back_computations mb ON mb.computation_id = pa.computation_id
                JOIN company_income ci ON ci.income_id = mb.income_id
                JOIN users u ON u.user_id = pa.user_id WHERE ci.income_year = ?""", year),
                new String[] {"Member", "Savings basis", "Share %", "Credited"},
                new String[] {"username", "savings_basis", "share_percentage", "allocated_amount"});
    }

    // ---------- reports ----------
    private void reports() throws SQLException {
        Cli.title("LOAN STATISTICS");
        Cli.table(Db.query(c, """
                SELECT COUNT(*) AS loans, SUM(loan_amount) AS total, AVG(loan_amount) AS average,
                       MIN(loan_amount) AS smallest, MAX(loan_amount) AS largest FROM loan_applications"""),
                new String[] {"Loans", "Total", "Average", "Smallest", "Largest"},
                new String[] {"loans", "total", "average", "smallest", "largest"});
        System.out.println("\nLoans by status:");
        Cli.table(Db.query(c, "SELECT status, COUNT(*) AS loans, SUM(loan_amount) AS amount FROM loan_applications "
                + "GROUP BY status ORDER BY status"),
                new String[] {"Status", "Loans", "Amount"}, new String[] {"status", "loans", "amount"});
        System.out.println("\nTotal loans per user:");
        Cli.table(Db.query(c, """
                SELECT u.username, COUNT(l.loan_id) AS loans, SUM(l.loan_amount) AS borrowed
                FROM users u JOIN loan_applications l ON l.user_id = u.user_id
                GROUP BY u.user_id, u.username ORDER BY borrowed DESC"""),
                new String[] {"Member", "Loans", "Total borrowed"}, new String[] {"username", "loans", "borrowed"});
        System.out.println("\nSavings transactions by type (completed):");
        Cli.table(Db.query(c, "SELECT txn_type, COUNT(*) AS txns, SUM(amount) AS total FROM savings_transactions "
                + "WHERE status = 'Completed' GROUP BY txn_type"),
                new String[] {"Type", "Transactions", "Total"}, new String[] {"txn_type", "txns", "total"});
        System.out.println("\nSavings by account type:");
        Cli.table(Db.query(c, """
                SELECT u.account_type, COUNT(*) AS accounts, SUM(sa.balance) AS total, AVG(sa.balance) AS average
                FROM savings_accounts sa JOIN users u ON u.user_id = sa.user_id GROUP BY u.account_type"""),
                new String[] {"Type", "Accounts", "Total", "Average"},
                new String[] {"account_type", "accounts", "total", "average"});
    }

    private void activityLog() throws SQLException {
        Cli.title("ADMINISTRATIVE ACTIVITY (latest 30)");
        Cli.table(Db.query(c, """
                SELECT l.log_id, l.logged_at, a.username, l.action, l.target_table, l.target_id, l.description
                FROM admin_activity_logs l JOIN administrators a ON a.admin_id = l.admin_id
                ORDER BY l.log_id DESC LIMIT 30"""),
                new String[] {"#", "When", "Admin", "Action", "Table", "Target", "Details"},
                new String[] {"log_id", "logged_at", "username", "action", "target_table", "target_id", "description"});
    }
}
