-- Sample data (insert order respects foreign keys)
PRAGMA foreign_keys = ON;

-- PART 2: INSERT  (sample data; insert order respects foreign keys)
-- =====================================================================

INSERT INTO administrators (username, password_hash, full_name, email, role) VALUES
('jdelacruz', 'hash_a1', 'Juan Dela Cruz', 'juan@company.ph', 'Super Admin'),
('msantos',   'hash_a2', 'Maria Santos',   'maria@company.ph', 'Staff');

INSERT INTO users (username, password_hash, email, account_type, account_status, created_at) VALUES
('jreyes',    'hash_u1', 'jreyes@mail.com',    'Basic',   'Active',   '2025-01-10 09:00:00'),
('mgarcia',   'hash_u2', 'mgarcia@mail.com',   'Premium', 'Active',   '2025-01-12 10:30:00'),
('lcruz',     'hash_u3', 'lcruz@mail.com',     'Basic',   'Active',   '2025-02-03 14:15:00'),
('pbautista', 'hash_u4', 'pbautista@mail.com', 'Premium', 'Active',   '2025-02-20 11:00:00'),
('tlim',      'hash_u5', 'tlim@mail.com',      'Basic',   'Disabled', '2026-10-30 16:45:00');

INSERT INTO user_profiles (user_id, first_name, last_name, birth_date, gender, civil_status, phone, address) VALUES
(1, 'Jose',   'Reyes',    '1990-05-14', 'Male',   'Single',  '09171234501', 'Cebu City'),
(2, 'Maria',  'Garcia',   '1985-11-02', 'Female', 'Married', '09171234502', 'Talisay City'),
(3, 'Luis',   'Cruz',     '1993-03-21', 'Male',   'Single',  '09171234503', 'Minglanilla, Cebu'),
(4, 'Paula',  'Bautista', '1988-08-09', 'Female', 'Married', '09171234504', 'Mandaue City'),
(5, 'Thomas', 'Lim',      '1995-12-30', 'Male',   'Single',  '09171234505', 'Lapu-Lapu City');

INSERT INTO bank_details (user_id, bank_name, account_name, account_number) VALUES
(1, 'BDO',  'Jose Reyes',     '001234567890'),
(2, 'BPI',  'Maria Garcia',   '009876543210'),
(3, 'UCPB', 'Luis Cruz',      '004455667788'),
(4, 'BDO',  'Paula Bautista', '001122334455'),
(5, 'BPI',  'Thomas Lim',     '007766554433');

INSERT INTO registration_applications (user_id, requested_type, status, submitted_at) VALUES
(1, 'Basic',   'Approved', '2025-01-10 09:00:00'),
(2, 'Premium', 'Approved', '2025-01-12 10:30:00'),
(3, 'Basic',   'Approved', '2025-02-03 14:15:00'),
(4, 'Premium', 'Approved', '2025-02-20 11:00:00'),
(5, 'Basic',   'Pending',  '2026-10-30 16:45:00');

INSERT INTO application_documents (application_id, document_type, file_path) VALUES
(1, 'Government ID',   '/docs/1/gov_id.pdf'),
(1, 'Proof of Income', '/docs/1/payslip.pdf'),
(2, 'Government ID',   '/docs/2/gov_id.pdf'),
(3, 'Government ID',   '/docs/3/gov_id.pdf'),
(4, 'Government ID',   '/docs/4/gov_id.pdf'),
(5, 'Government ID',   '/docs/5/gov_id.pdf');

INSERT INTO employment_info (application_id, employer_name, position, employment_type, monthly_income, date_started) VALUES
(1, 'Cebu Tech Corp',  'Developer',       'Employed',       45000.00, '2018-06-01'),
(2, 'Garcia Bakeshop', 'Owner',           'Business Owner', 90000.00, '2015-01-15'),
(3, 'City Hospital',   'Nurse',           'Employed',       30000.00, '2020-09-01'),
(4, 'Self',            'Online Seller',   'Self-Employed',  60000.00, '2019-03-10'),
(5, 'BPO Solutions',   'Agent',           'Employed',       25000.00, '2022-01-10');

INSERT INTO registration_reviews (application_id, admin_id, decision, remarks, reviewed_at) VALUES
(1, 1, 'Approved', 'Documents complete', '2025-01-11 09:00:00'),
(2, 1, 'Approved', 'Documents complete', '2025-01-13 09:00:00'),
(3, 2, 'Approved', 'Documents complete', '2025-02-04 09:00:00'),
(4, 2, 'Approved', 'Documents complete', '2025-02-21 09:00:00');

INSERT INTO loan_applications (user_id, loan_amount, interest_rate, term_months, purpose, status, applied_at) VALUES
(1,  50000.00, 12.00, 12, 'Home repair',      'Approved', '2026-06-20 10:00:00'),
(2, 100000.00, 10.00, 24, 'Bakery expansion', 'Approved', '2026-07-15 10:00:00'),
(3,  30000.00, 12.00,  6, 'Tuition fee',      'Pending',  '2026-11-02 10:00:00'),
(1,  20000.00, 12.00,  6, 'Gadget purchase',  'Rejected', '2026-03-05 10:00:00');

INSERT INTO loan_reviews (loan_id, admin_id, decision, remarks, reviewed_at) VALUES
(1, 1, 'Approved', 'Meets income requirement', '2026-06-25 09:00:00'),
(2, 1, 'Approved', 'Premium member',           '2026-07-20 09:00:00'),
(4, 2, 'Rejected', 'Insufficient income',      '2026-03-08 09:00:00');

INSERT INTO loan_transactions (loan_id, txn_type, amount, txn_date, reference_no) VALUES
(1, 'Disbursement', 50000.00,  '2026-07-01 10:00:00', 'LTX-0001'),
(2, 'Disbursement', 100000.00, '2026-08-01 10:00:00', 'LTX-0002'),
(1, 'Repayment',    4666.67,   '2026-08-01 15:00:00', 'LTX-0003'),
(1, 'Repayment',    4666.67,   '2026-09-01 15:00:00', 'LTX-0004'),
(2, 'Repayment',    5000.00,   '2026-09-01 16:00:00', 'LTX-0005'),
(2, 'Repayment',    5000.00,   '2026-10-01 16:00:00', 'LTX-0006');

-- Simple-interest schedule: principal = amount/term, interest = amount*rate/12
INSERT INTO payment_schedules (loan_id, installment_no, due_date, principal_due, interest_due) VALUES
(1, 1, '2026-08-01', 4166.67, 500.00),
(1, 2, '2026-09-01', 4166.67, 500.00),
(1, 3, '2026-10-01', 4166.67, 500.00),
(1, 4, '2026-11-01', 4166.67, 500.00),
(2, 1, '2026-09-01', 4166.67, 833.33),
(2, 2, '2026-10-01', 4166.67, 833.33);

INSERT INTO billing_statements (schedule_id, statement_date, penalty_amount, total_due, payment_status) VALUES
(1, '2026-07-25',   0.00, 4666.67, 'Paid'),
(2, '2026-08-25',   0.00, 4666.67, 'Paid'),
(3, '2026-09-25', 100.00, 4766.67, 'Overdue'),   -- late: 100 penalty
(5, '2026-08-25',   0.00, 5000.00, 'Paid'),
(6, '2026-09-25',   0.00, 5000.00, 'Paid');

INSERT INTO loan_payments (statement_id, loan_txn_id, amount_paid, payment_date, payment_method) VALUES
(1, 3, 4666.67, '2026-08-01', 'Bank Transfer'),
(2, 4, 4666.67, '2026-09-01', 'E-Wallet'),
(4, 5, 5000.00, '2026-09-01', 'Bank Transfer'),
(5, 6, 5000.00, '2026-10-01', 'Bank Transfer');

-- Savings (balances shown are the final balances after all transactions below)
INSERT INTO savings_accounts (user_id, balance, opened_at) VALUES
(1,  5000.00, '2025-01-15 09:00:00'),
(2, 32500.00, '2025-01-16 09:00:00'),
(3,  3000.00, '2025-02-10 09:00:00'),
(4, 19500.00, '2025-02-25 09:00:00');

INSERT INTO savings_transactions (savings_id, txn_type, amount, status, txn_date, reference_no) VALUES
(1, 'Deposit',    5000.00, 'Completed', '2025-03-01 10:00:00', 'STX-0001'),
(2, 'Deposit',   20000.00, 'Completed', '2025-03-05 10:00:00', 'STX-0002'),
(2, 'Deposit',   10000.00, 'Completed', '2025-06-05 10:00:00', 'STX-0003'),
(3, 'Deposit',    3000.00, 'Completed', '2025-04-01 10:00:00', 'STX-0004'),
(4, 'Deposit',   15000.00, 'Completed', '2025-04-10 10:00:00', 'STX-0005'),
(2, 'Withdrawal', 5000.00, 'Completed', '2025-10-12 10:00:00', 'STX-0006'),
(2, 'MoneyBack',  7500.00, 'Completed', '2026-01-15 09:00:00', 'STX-0007'),
(4, 'MoneyBack',  4500.00, 'Completed', '2026-01-15 09:00:00', 'STX-0008');

INSERT INTO withdrawal_requests (savings_id, amount, status, requested_at, admin_id, reviewed_at, remarks, savings_txn_id) VALUES
(2, 5000.00, 'Approved', '2025-10-10 09:00:00', 2, '2025-10-11 09:00:00', 'OK',                 6),
(1, 2000.00, 'Pending',  '2026-11-05 09:00:00', NULL, NULL, NULL,                               NULL),
(3, 9000.00, 'Rejected', '2026-09-01 09:00:00', 2, '2026-09-02 09:00:00', 'Exceeds balance',    NULL);

-- Money back for 2025: pool = 1% of 1,200,000 = 12,000, split by savings share
INSERT INTO company_income (income_year, total_income, recorded_by) VALUES
(2025, 1200000.00, 1);

INSERT INTO money_back_computations (income_id, pool_percentage, pool_amount, total_premium_savings, computed_by) VALUES
(1, 1.00, 12000.00, 40000.00, 1);

INSERT INTO premium_allocations (computation_id, user_id, savings_basis, share_percentage, allocated_amount, savings_txn_id) VALUES
(1, 2, 25000.00, 62.5000, 7500.00, 7),
(1, 4, 15000.00, 37.5000, 4500.00, 8);

INSERT INTO admin_activity_logs (admin_id, action, target_table, target_id, description) VALUES
(1, 'APPROVE_REGISTRATION', 'registration_applications', 1, 'Approved jreyes'),
(1, 'APPROVE_LOAN',         'loan_applications',         1, 'Approved loan 1'),
(2, 'REJECT_LOAN',          'loan_applications',         4, 'Rejected loan 4'),
(2, 'APPROVE_WITHDRAWAL',   'withdrawal_requests',       1, 'Approved withdrawal 1'),
(2, 'REJECT_WITHDRAWAL',    'withdrawal_requests',       3, 'Rejected withdrawal 3'),
(1, 'COMPUTE_MONEY_BACK',   'money_back_computations',   1, 'Year 2025 computation');

-- =====================================================================
