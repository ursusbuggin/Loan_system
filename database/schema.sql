-- SQLite 3 schema. Create DB with: sqlite3 loan_savings.db < schema.sql
PRAGMA foreign_keys = ON;

-- PART 1: TABLES (DDL)
-- =====================================================================

-- ---------- Administrator Management ----------
CREATE TABLE administrators (
    admin_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(100) NOT NULL UNIQUE,
    role           VARCHAR(30)  NOT NULL DEFAULT 'Staff'
                   CHECK (role IN ('Super Admin','Staff')),
    status         VARCHAR(10)  NOT NULL DEFAULT 'Active'
                   CHECK (status IN ('Active','Disabled')),
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------- User Management ----------
CREATE TABLE users (
    user_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,           -- login credential (hashed)
    email           VARCHAR(100) NOT NULL UNIQUE,
    account_type    VARCHAR(10)  NOT NULL DEFAULT 'Basic'
                    CHECK (account_type IN ('Basic','Premium')),
    account_status  VARCHAR(10)  NOT NULL DEFAULT 'Disabled'
                    CHECK (account_status IN ('Active','Disabled')),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_profiles (                           -- 1:1 with users
    user_id        INT PRIMARY KEY,
    first_name     VARCHAR(50)  NOT NULL,
    middle_name    VARCHAR(50),
    last_name      VARCHAR(50)  NOT NULL,
    birth_date     DATE         NOT NULL,
    gender         VARCHAR(10)  CHECK (gender IN ('Male','Female','Other')),
    civil_status   VARCHAR(15),
    phone          VARCHAR(20)  NOT NULL,
    address        VARCHAR(255) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE bank_details (
    bank_detail_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         INT          NOT NULL,
    bank_name       VARCHAR(100) NOT NULL,
    account_name    VARCHAR(100) NOT NULL,
    account_number  VARCHAR(30)  NOT NULL,
    is_primary      BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (bank_name, account_number),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- ---------- Registration and Verification ----------
CREATE TABLE registration_applications (
    application_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          INT         NOT NULL,
    requested_type   VARCHAR(10) NOT NULL DEFAULT 'Basic'
                     CHECK (requested_type IN ('Basic','Premium')),
    status           VARCHAR(10) NOT NULL DEFAULT 'Pending'
                     CHECK (status IN ('Pending','Approved','Rejected')),
    submitted_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE application_documents (
    document_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    application_id INT          NOT NULL,
    document_type  VARCHAR(50)  NOT NULL,              -- e.g. Government ID, Proof of Billing
    file_path      VARCHAR(255) NOT NULL,
    uploaded_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (application_id) REFERENCES registration_applications(application_id)
        ON DELETE CASCADE
);

CREATE TABLE employment_info (
    employment_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    application_id  INT           NOT NULL UNIQUE,
    employer_name   VARCHAR(100)  NOT NULL,
    position        VARCHAR(100)  NOT NULL,
    employment_type VARCHAR(20)   NOT NULL
                    CHECK (employment_type IN ('Employed','Self-Employed','Business Owner')),
    monthly_income  DECIMAL(12,2) NOT NULL CHECK (monthly_income >= 0),
    date_started    DATE,
    FOREIGN KEY (application_id) REFERENCES registration_applications(application_id)
        ON DELETE CASCADE
);

CREATE TABLE registration_reviews (                    -- approval / rejection records
    review_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    application_id INT         NOT NULL UNIQUE,
    admin_id       INT         NOT NULL,
    decision       VARCHAR(10) NOT NULL CHECK (decision IN ('Approved','Rejected')),
    remarks        VARCHAR(255),
    reviewed_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (application_id) REFERENCES registration_applications(application_id),
    FOREIGN KEY (admin_id)       REFERENCES administrators(admin_id)
);

-- ---------- Loan Management ----------
CREATE TABLE loan_applications (
    loan_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id        INT           NOT NULL,
    loan_amount    DECIMAL(12,2) NOT NULL CHECK (loan_amount > 0),
    interest_rate  DECIMAL(5,2)  NOT NULL CHECK (interest_rate >= 0),   -- % per year
    term_months    INT           NOT NULL CHECK (term_months > 0),      -- repayment period
    purpose        VARCHAR(255),
    status         VARCHAR(10)   NOT NULL DEFAULT 'Pending'
                   CHECK (status IN ('Pending','Approved','Rejected','Paid')),
    applied_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE loan_reviews (
    review_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id      INT         NOT NULL UNIQUE,
    admin_id     INT         NOT NULL,
    decision     VARCHAR(10) NOT NULL CHECK (decision IN ('Approved','Rejected')),
    remarks      VARCHAR(255),
    reviewed_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (loan_id)  REFERENCES loan_applications(loan_id),
    FOREIGN KEY (admin_id) REFERENCES administrators(admin_id)
);

CREATE TABLE loan_transactions (
    loan_txn_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id       INT           NOT NULL,
    txn_type      VARCHAR(15)   NOT NULL CHECK (txn_type IN ('Disbursement','Repayment')),
    amount        DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    txn_date      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reference_no  VARCHAR(40)   NOT NULL UNIQUE,
    FOREIGN KEY (loan_id) REFERENCES loan_applications(loan_id)
);

-- ---------- Billing Management ----------
CREATE TABLE payment_schedules (                       -- monthly payment schedule
    schedule_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id         INT           NOT NULL,
    installment_no  INT           NOT NULL CHECK (installment_no > 0),
    due_date        DATE          NOT NULL,
    principal_due   DECIMAL(12,2) NOT NULL CHECK (principal_due >= 0),
    interest_due    DECIMAL(12,2) NOT NULL CHECK (interest_due >= 0),
    UNIQUE (loan_id, installment_no),
    FOREIGN KEY (loan_id) REFERENCES loan_applications(loan_id)
);

CREATE TABLE billing_statements (                      -- billing history = all statements
    statement_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id      INT           NOT NULL UNIQUE,
    statement_date   DATE          NOT NULL,
    penalty_amount   DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (penalty_amount >= 0),
    total_due        DECIMAL(12,2) NOT NULL CHECK (total_due >= 0),   -- principal+interest+penalty
    payment_status   VARCHAR(10)   NOT NULL DEFAULT 'Unpaid'
                     CHECK (payment_status IN ('Unpaid','Paid','Overdue')),
    FOREIGN KEY (schedule_id) REFERENCES payment_schedules(schedule_id)
);

CREATE TABLE loan_payments (
    payment_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    statement_id   INT           NOT NULL,
    loan_txn_id    INT           NOT NULL UNIQUE,      -- the Repayment transaction
    amount_paid    DECIMAL(12,2) NOT NULL CHECK (amount_paid > 0),
    payment_date   DATE          NOT NULL,
    payment_method VARCHAR(20)   NOT NULL
                   CHECK (payment_method IN ('Bank Transfer','Cash','E-Wallet','Savings')),
    FOREIGN KEY (statement_id) REFERENCES billing_statements(statement_id),
    FOREIGN KEY (loan_txn_id)  REFERENCES loan_transactions(loan_txn_id)
);

-- ---------- Savings Management ----------
CREATE TABLE savings_accounts (
    savings_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INT           NOT NULL UNIQUE,        -- one savings account per user
    balance      DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    opened_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE savings_transactions (
    savings_txn_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    savings_id      INT           NOT NULL,
    txn_type        VARCHAR(15)   NOT NULL
                    CHECK (txn_type IN ('Deposit','Withdrawal','MoneyBack')),
    amount          DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    status          VARCHAR(10)   NOT NULL DEFAULT 'Pending'
                    CHECK (status IN ('Pending','Completed','Failed')),
    txn_date        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reference_no    VARCHAR(40)   NOT NULL UNIQUE,
    FOREIGN KEY (savings_id) REFERENCES savings_accounts(savings_id)
);

CREATE TABLE withdrawal_requests (
    request_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    savings_id      INT           NOT NULL,
    amount          DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    status          VARCHAR(10)   NOT NULL DEFAULT 'Pending'
                    CHECK (status IN ('Pending','Approved','Rejected')),
    requested_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    admin_id        INT,                                -- filled when reviewed
    reviewed_at     DATETIME,
    remarks         VARCHAR(255),
    savings_txn_id  INT UNIQUE,                         -- created upon approval
    FOREIGN KEY (savings_id)     REFERENCES savings_accounts(savings_id),
    FOREIGN KEY (admin_id)       REFERENCES administrators(admin_id),
    FOREIGN KEY (savings_txn_id) REFERENCES savings_transactions(savings_txn_id)
);

-- ---------- Money Back Earnings ----------
CREATE TABLE company_income (
    income_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    income_year     INT           NOT NULL UNIQUE,
    total_income    DECIMAL(15,2) NOT NULL CHECK (total_income >= 0),
    recorded_by     INT           NOT NULL,
    recorded_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (recorded_by) REFERENCES administrators(admin_id)
);

CREATE TABLE money_back_computations (
    computation_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    income_id             INT           NOT NULL UNIQUE,
    pool_percentage       DECIMAL(5,2)  NOT NULL CHECK (pool_percentage BETWEEN 0 AND 100),
    pool_amount           DECIMAL(15,2) NOT NULL CHECK (pool_amount >= 0),
    total_premium_savings DECIMAL(15,2) NOT NULL CHECK (total_premium_savings >= 0),
    computed_by           INT           NOT NULL,
    computed_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (income_id)   REFERENCES company_income(income_id),
    FOREIGN KEY (computed_by) REFERENCES administrators(admin_id)
);

CREATE TABLE premium_allocations (
    allocation_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    computation_id    INT           NOT NULL,
    user_id           INT           NOT NULL,
    savings_basis     DECIMAL(12,2) NOT NULL CHECK (savings_basis >= 0), -- balance used
    share_percentage  DECIMAL(7,4)  NOT NULL CHECK (share_percentage BETWEEN 0 AND 100),
    allocated_amount  DECIMAL(12,2) NOT NULL CHECK (allocated_amount >= 0),
    savings_txn_id    INT UNIQUE,                                         -- the savings credit
    UNIQUE (computation_id, user_id),
    FOREIGN KEY (computation_id) REFERENCES money_back_computations(computation_id),
    FOREIGN KEY (user_id)        REFERENCES users(user_id),
    FOREIGN KEY (savings_txn_id) REFERENCES savings_transactions(savings_txn_id)
);

-- ---------- Administrative activity ----------
CREATE TABLE admin_activity_logs (
    log_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    admin_id      INT          NOT NULL,
    action        VARCHAR(50)  NOT NULL,           -- e.g. APPROVE_LOAN
    target_table  VARCHAR(50)  NOT NULL,
    target_id     INT          NOT NULL,
    description   VARCHAR(255),
    logged_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (admin_id) REFERENCES administrators(admin_id)
);

-- =====================================================================
