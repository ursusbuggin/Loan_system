# Loan and Savings Management System (Java console + database)

A console application on top of a relational database (21 tables, SQLite via JDBC).
It covers every component in the project brief: users, registration and verification,
loans, billing, savings, money back earnings and administrator management.

> **About "LETOS":** I could not identify a database platform called LETOS, so the
> database is standard SQL running on SQLite (no server to install). The SQL files in
> `database/` use only common syntax; if LETOS differs, adjust the data types in
> `database/schema.sql` (e.g. `INTEGER PRIMARY KEY AUTOINCREMENT`).

## Run it

Requirements: Java 17+ and Maven (Maven downloads the SQLite driver the first time).

    ./run.sh          (Windows: run.bat)

The database file `loan_savings.db` is created automatically with sample data.
`./run.sh --reset` rebuilds it from scratch.

Without Maven: download `sqlite-jdbc-3.45.1.0.jar` and `slf4j-api-2.0.9.jar` into `lib/`, then

    javac -cp "lib/*" -d out src/main/java/loansavings/*.java
    java -cp "out:lib/*" loansavings.Main          (Windows: out;lib\*)

## Demo accounts

| Role | Username | Password | Notes |
|---|---|---|---|
| Member | jreyes | user123 | Basic, 1 active loan, savings |
| Member | mgarcia | user123 | Premium, 1 active loan, money back credit |
| Member | lcruz | user123 | Basic, has a pending loan to review |
| Member | pbautista | user123 | Premium |
| Member | tlim | user123 | Disabled; registration pending approval |
| Admin | jdelacruz | admin123 | Super Admin |
| Admin | msantos | admin123 | Staff |

Suggested demo: log in as `jdelacruz` -> approve tlim's registration, approve lcruz's loan,
approve jreyes' pending withdrawal, run the billing cycle, record 2026 income and compute money
back, then view Reports and the Activity log. Log in as the members to see the results.

## Business rules implemented

- New registrations start **Disabled**; admin approval activates the account, sets its type and opens a savings account.
- Loans: Basic max 50,000 at 12% a year; Premium max 500,000 at 10%; up to 36 months; one pending application at a time.
- Approving a loan records the disbursement and generates a monthly schedule (equal principal, flat monthly interest, last installment absorbs rounding).
- Billing: statements for installments due within 30 days; a one-time 2% penalty once overdue; payments must be in full and in installment order; the loan becomes **Paid** after the last installment.
- Savings: deposits are immediate; withdrawals need admin approval and cannot exceed the available balance.
- Money back: admin enters yearly company income and a percentage; the pool is split among active Premium members in proportion to their savings and credited as `MoneyBack` savings transactions. One computation per year.
- Every admin decision is written to `admin_activity_logs`. Financial history cannot be deleted (foreign keys).

## Project layout

    database/schema.sql          21 tables with PK, FK, NOT NULL, UNIQUE, CHECK, DEFAULT
    database/sample_data.sql     sample users, loans, billing, savings, money back
    database/demo_operations.sql SELECT, JOIN, WHERE/ORDER BY, aggregates, GROUP BY, UPDATE, DELETE
    database/erd.mermaid         Crow's Foot ERD (open at https://mermaid.live to export an image)
    src/main/java/loansavings/   Main, Cli, Db, Security, Billing, Registration, UserMenu, AdminMenu, Row
    pom.xml, run.sh, run.bat

To try the SQL operations by hand (on a scratch copy; the demo file modifies data):

    sqlite3 scratch.db < database/schema.sql
    sqlite3 scratch.db < database/sample_data.sql
    sqlite3 scratch.db < database/demo_operations.sql
