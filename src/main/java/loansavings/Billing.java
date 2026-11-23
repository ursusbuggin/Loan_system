package loansavings;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Billing rules: statements, penalties and loan payments. */
final class Billing {
    static final double PENALTY_RATE = 0.02; // one-time 2% of the installment once overdue

    private Billing() {}

    record PayResult(double amount, boolean loanClosed) {}

    /** Return the statement for a schedule row, creating it if it does not exist. */
    static Row ensureStatement(Connection c, long scheduleId) throws SQLException {
        Row st = Db.one(c, "SELECT * FROM billing_statements WHERE schedule_id = ?", scheduleId);
        if (st != null) return st;
        Row ps = Db.one(c, "SELECT * FROM payment_schedules WHERE schedule_id = ?", scheduleId);
        double total = Db.round2(ps.dbl("principal_due") + ps.dbl("interest_due"));
        Db.update(c, "INSERT INTO billing_statements (schedule_id, statement_date, penalty_amount, total_due, "
                + "payment_status) VALUES (?, ?, 0, ?, 'Unpaid')", scheduleId, LocalDate.now().toString(), total);
        return Db.one(c, "SELECT * FROM billing_statements WHERE schedule_id = ?", scheduleId);
    }

    /** Mark overdue and add the penalty once. Returns true if the statement changed. */
    static boolean applyPenalty(Connection c, long statementId) throws SQLException {
        Row r = Db.one(c, """
                SELECT bs.payment_status, bs.penalty_amount, ps.due_date, ps.principal_due, ps.interest_due
                FROM billing_statements bs
                JOIN payment_schedules ps ON ps.schedule_id = bs.schedule_id
                WHERE bs.statement_id = ?""", statementId);
        if (r.str("payment_status").equals("Paid") || r.str("due_date").compareTo(LocalDate.now().toString()) >= 0)
            return false;
        double base = r.dbl("principal_due") + r.dbl("interest_due");
        double penalty = r.dbl("penalty_amount");
        if (penalty == 0) penalty = Db.round2(base * PENALTY_RATE);
        boolean changed = !r.str("payment_status").equals("Overdue") || r.dbl("penalty_amount") != penalty;
        Db.update(c, "UPDATE billing_statements SET payment_status = 'Overdue', penalty_amount = ?, total_due = ? "
                + "WHERE statement_id = ?", penalty, Db.round2(base + penalty), statementId);
        return changed;
    }

    /** Generate statements for installments due within 30 days and penalise late ones. */
    static int[] runCycle(Connection c) throws SQLException {
        int created = 0, penalised = 0;
        List<Row> due = Db.query(c, """
                SELECT ps.schedule_id FROM payment_schedules ps
                JOIN loan_applications l ON l.loan_id = ps.loan_id
                WHERE l.status = 'Approved' AND ps.due_date <= date('now', '+30 days')
                  AND NOT EXISTS (SELECT 1 FROM billing_statements b WHERE b.schedule_id = ps.schedule_id)""");
        for (Row r : due) {
            ensureStatement(c, r.lng("schedule_id"));
            created++;
        }
        for (Row r : Db.query(c, "SELECT statement_id FROM billing_statements WHERE payment_status <> 'Paid'")) {
            if (applyPenalty(c, r.lng("statement_id"))) penalised++;
        }
        return new int[] {created, penalised};
    }

    /** Record a full payment; close the loan when every installment is paid. */
    static PayResult pay(Connection c, long statementId, String method) throws SQLException {
        Row st = Db.one(c, """
                SELECT bs.total_due, ps.loan_id FROM billing_statements bs
                JOIN payment_schedules ps ON ps.schedule_id = bs.schedule_id
                WHERE bs.statement_id = ?""", statementId);
        long loanId = st.lng("loan_id");
        double amount = st.dbl("total_due");
        long txnId = Db.insert(c, "INSERT INTO loan_transactions (loan_id, txn_type, amount, reference_no) "
                + "VALUES (?, 'Repayment', ?, ?)", loanId, amount, Db.newRef("LTX"));
        Db.update(c, "INSERT INTO loan_payments (statement_id, loan_txn_id, amount_paid, payment_date, "
                + "payment_method) VALUES (?, ?, ?, ?, ?)", statementId, txnId, amount, LocalDate.now().toString(), method);
        Db.update(c, "UPDATE billing_statements SET payment_status = 'Paid' WHERE statement_id = ?", statementId);
        long paid = Db.one(c, """
                SELECT COUNT(*) AS n FROM billing_statements bs
                JOIN payment_schedules ps ON ps.schedule_id = bs.schedule_id
                WHERE ps.loan_id = ? AND bs.payment_status = 'Paid'""", loanId).lng("n");
        long term = Db.one(c, "SELECT term_months FROM loan_applications WHERE loan_id = ?", loanId).lng("term_months");
        boolean closed = paid >= term;
        if (closed) Db.update(c, "UPDATE loan_applications SET status = 'Paid' WHERE loan_id = ?", loanId);
        return new PayResult(amount, closed);
    }
}
