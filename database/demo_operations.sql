-- SELECT / JOIN / aggregate / UPDATE / DELETE demonstrations.
-- WARNING: modifies data. Run on a scratch copy of the database.
PRAGMA foreign_keys = ON;

-- PART 3: SELECT, WHERE, ORDER BY
-- =====================================================================

-- All users
SELECT * FROM users;

-- Active premium members, newest first
SELECT user_id, username, email, created_at
FROM users
WHERE account_type = 'Premium' AND account_status = 'Active'
ORDER BY created_at DESC;

-- Loans above 25,000 sorted by amount
SELECT loan_id, user_id, loan_amount, status
FROM loan_applications
WHERE loan_amount > 25000
ORDER BY loan_amount DESC;

-- Overdue billing statements
SELECT * FROM billing_statements WHERE payment_status = 'Overdue';

-- =====================================================================
-- PART 4: JOINs
-- =====================================================================

-- Users with their personal info and bank details
SELECT u.username, p.first_name || ' ' || p.last_name AS full_name,
       u.account_type, b.bank_name, b.account_number
FROM users u
JOIN user_profiles p ON p.user_id = u.user_id
JOIN bank_details  b ON b.user_id = u.user_id
ORDER BY full_name;

-- Registration applications with documents count and decision
SELECT u.username, ra.status, ra.submitted_at,
       rr.decision, a.full_name AS reviewed_by
FROM registration_applications ra
JOIN users u                  ON u.user_id = ra.user_id
LEFT JOIN registration_reviews rr ON rr.application_id = ra.application_id
LEFT JOIN administrators a    ON a.admin_id = rr.admin_id;

-- Loans with borrower name and approving administrator
SELECT l.loan_id, u.username, l.loan_amount, l.interest_rate, l.term_months,
       l.status, a.full_name AS reviewed_by, lr.decision, lr.remarks
FROM loan_applications l
JOIN users u                 ON u.user_id = l.user_id
LEFT JOIN loan_reviews lr    ON lr.loan_id = l.loan_id
LEFT JOIN administrators a   ON a.admin_id = lr.admin_id
ORDER BY l.loan_id;

-- Billing history: schedule + statement + payment
SELECT u.username, ps.installment_no, ps.due_date, bs.total_due,
       bs.penalty_amount, bs.payment_status, lp.amount_paid, lp.payment_date
FROM billing_statements bs
JOIN payment_schedules ps   ON ps.schedule_id = bs.schedule_id
JOIN loan_applications l    ON l.loan_id = ps.loan_id
JOIN users u                ON u.user_id = l.user_id
LEFT JOIN loan_payments lp  ON lp.statement_id = bs.statement_id
ORDER BY u.username, ps.due_date;

-- Savings account holders with balance
SELECT u.username, sa.balance
FROM savings_accounts sa
JOIN users u ON u.user_id = sa.user_id
ORDER BY sa.balance DESC;

-- Withdrawal requests with reviewer
SELECT wr.request_id, u.username, wr.amount, wr.status, a.full_name AS reviewed_by
FROM withdrawal_requests wr
JOIN savings_accounts sa ON sa.savings_id = wr.savings_id
JOIN users u             ON u.user_id = sa.user_id
LEFT JOIN administrators a ON a.admin_id = wr.admin_id;

-- Money back allocation per premium member
SELECT ci.income_year, u.username, pa.savings_basis, pa.share_percentage, pa.allocated_amount
FROM premium_allocations pa
JOIN money_back_computations mb ON mb.computation_id = pa.computation_id
JOIN company_income ci          ON ci.income_id = mb.income_id
JOIN users u                    ON u.user_id = pa.user_id;

-- =====================================================================
-- PART 5: AGGREGATE FUNCTIONS and GROUP BY
-- =====================================================================

-- Overall loan statistics
SELECT COUNT(*) AS total_loans, SUM(loan_amount) AS total_amount,
       AVG(loan_amount) AS avg_amount, MIN(loan_amount) AS smallest,
       MAX(loan_amount) AS largest
FROM loan_applications;

-- Total loans per user
SELECT u.username, COUNT(l.loan_id) AS loan_count, SUM(l.loan_amount) AS total_borrowed
FROM users u
JOIN loan_applications l ON l.user_id = u.user_id
GROUP BY u.user_id, u.username
ORDER BY total_borrowed DESC;

-- Loans by status
SELECT status, COUNT(*) AS total, SUM(loan_amount) AS amount
FROM loan_applications
GROUP BY status;

-- Total savings transactions per type
SELECT txn_type, COUNT(*) AS total_txns, SUM(amount) AS total_amount
FROM savings_transactions
WHERE status = 'Completed'
GROUP BY txn_type;

-- Total savings per account type
SELECT u.account_type, COUNT(*) AS accounts, SUM(sa.balance) AS total_balance,
       AVG(sa.balance) AS avg_balance
FROM savings_accounts sa
JOIN users u ON u.user_id = sa.user_id
GROUP BY u.account_type;

-- Total payments collected per loan, only loans with more than 1 payment
SELECT ps.loan_id, COUNT(lp.payment_id) AS payments, SUM(lp.amount_paid) AS total_paid
FROM loan_payments lp
JOIN billing_statements bs ON bs.statement_id = lp.statement_id
JOIN payment_schedules ps  ON ps.schedule_id = bs.schedule_id
GROUP BY ps.loan_id
HAVING COUNT(lp.payment_id) > 1;

-- Administrator workload
SELECT a.full_name, COUNT(*) AS activities
FROM admin_activity_logs l
JOIN administrators a ON a.admin_id = l.admin_id
GROUP BY a.admin_id, a.full_name;

-- Integrity check: stored balance vs sum of completed transactions (should match)
SELECT sa.savings_id, sa.balance,
       SUM(CASE WHEN st.txn_type = 'Withdrawal' THEN -st.amount ELSE st.amount END) AS computed_balance
FROM savings_accounts sa
JOIN savings_transactions st ON st.savings_id = sa.savings_id AND st.status = 'Completed'
GROUP BY sa.savings_id, sa.balance;

-- =====================================================================
-- PART 6: UPDATE
-- =====================================================================

-- Approve registration of user 5, activate the account
UPDATE registration_applications SET status = 'Approved' WHERE application_id = 5;
INSERT INTO registration_reviews (application_id, admin_id, decision, remarks)
VALUES (5, 2, 'Approved', 'Documents complete');
UPDATE users SET account_status = 'Active' WHERE user_id = 5;

-- Approve the pending loan application
UPDATE loan_applications SET status = 'Approved' WHERE loan_id = 3;
INSERT INTO loan_reviews (loan_id, admin_id, decision, remarks)
VALUES (3, 1, 'Approved', 'Meets requirements');

-- Approve the pending withdrawal: create transaction, deduct balance, link both
INSERT INTO savings_transactions (savings_id, txn_type, amount, status, reference_no)
VALUES (1, 'Withdrawal', 2000.00, 'Completed', 'STX-0009');

UPDATE withdrawal_requests
SET status = 'Approved', admin_id = 2, reviewed_at = CURRENT_TIMESTAMP, remarks = 'OK',
    savings_txn_id = (SELECT savings_txn_id FROM savings_transactions WHERE reference_no = 'STX-0009')
WHERE request_id = 2;

UPDATE savings_accounts SET balance = balance - 2000.00 WHERE savings_id = 1;

-- Disable an account
UPDATE users SET account_status = 'Disabled' WHERE username = 'tlim';

-- Upgrade a user to Premium
UPDATE users SET account_type = 'Premium' WHERE username = 'lcruz';

-- Record a payment of the overdue statement
UPDATE billing_statements SET payment_status = 'Paid' WHERE statement_id = 3;

-- =====================================================================
-- PART 7: DELETE  (only where business rules permit)
-- Rule: only Pending loans (no reviews/transactions) and unreferenced
-- records may be deleted. Financial history is protected by foreign keys.
-- =====================================================================

-- A new pending loan that the borrower cancels
INSERT INTO loan_applications (user_id, loan_amount, interest_rate, term_months, purpose)
VALUES (4, 15000.00, 12.00, 6, 'Cancelled request');

DELETE FROM loan_applications
WHERE purpose = 'Cancelled request' AND status = 'Pending';

-- Remove an uploaded document of a pending application
DELETE FROM application_documents
WHERE application_id = 5 AND document_type = 'Government ID';

-- This would FAIL (loan 1 has transactions/reviews) -> protects data integrity:
-- DELETE FROM loan_applications WHERE loan_id = 1;
