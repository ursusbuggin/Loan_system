package loansavings;

import java.sql.Connection;
import java.sql.SQLException;

/** Self-registration: creates a Disabled account and a Pending application. */
final class Registration {
    private Registration() {}

    static void run(Connection c) throws SQLException {
        Cli.title("NEW MEMBER REGISTRATION");
        String username;
        while (true) {
            username = Cli.ask("Username");
            if (Db.one(c, "SELECT 1 AS x FROM users WHERE username = ?", username) == null) break;
            System.out.println("  Username already taken.");
        }
        String password;
        while (true) {
            password = Cli.askPassword("Password (min 6 characters)");
            if (password.length() < 6) { System.out.println("  Too short."); continue; }
            if (password.equals(Cli.askPassword("Confirm password"))) break;
            System.out.println("  Passwords do not match.");
        }
        String email;
        while (true) {
            email = Cli.askEmail("Email");
            if (Db.one(c, "SELECT 1 AS x FROM users WHERE email = ?", email) == null) break;
            System.out.println("  Email already registered.");
        }

        System.out.println("\n-- Personal information --");
        String first = Cli.ask("First name"), last = Cli.ask("Last name");
        String birth = Cli.askDate("Birth date");
        String gender = Cli.askChoice("Gender", "Male", "Female", "Other");
        String phone = Cli.ask("Phone"), address = Cli.ask("Address");

        System.out.println("\n-- Employment information --");
        String employer = Cli.ask("Employer / business name"), position = Cli.ask("Position");
        String empType = Cli.askChoice("Employment type", "Employed", "Self-Employed", "Business Owner");
        double income = Cli.askMoney("Monthly income", 0, 100_000_000);

        System.out.println("\n-- Bank details --");
        String bank = Cli.ask("Bank name"), accName = Cli.ask("Account name"), accNo = Cli.ask("Account number");
        if (Db.one(c, "SELECT 1 AS x FROM bank_details WHERE bank_name = ? AND account_number = ?", bank, accNo) != null) {
            System.out.println("That bank account is already registered. Registration cancelled.");
            return;
        }

        System.out.println("\n-- Verification document --");
        String docType = Cli.ask("Document type (e.g. Government ID)");
        String docPath = Cli.ask("File path or reference");
        String type = Cli.askChoice("Requested account type", "Basic", "Premium");

        final String fUser = username, fEmail = email, fPass = password;
        final long[] ids = new long[2];
        Db.tx(c, () -> {
            long uid = Db.insert(c, "INSERT INTO users (username, password_hash, email, account_type, account_status) "
                    + "VALUES (?, ?, ?, 'Basic', 'Disabled')", fUser, Security.hash(fPass), fEmail);
            Db.update(c, "INSERT INTO user_profiles (user_id, first_name, last_name, birth_date, gender, phone, address) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)", uid, first, last, birth, gender, phone, address);
            Db.update(c, "INSERT INTO bank_details (user_id, bank_name, account_name, account_number) VALUES (?, ?, ?, ?)",
                    uid, bank, accName, accNo);
            long appId = Db.insert(c, "INSERT INTO registration_applications (user_id, requested_type) VALUES (?, ?)",
                    uid, type);
            Db.update(c, "INSERT INTO employment_info (application_id, employer_name, position, employment_type, "
                    + "monthly_income) VALUES (?, ?, ?, ?, ?)", appId, employer, position, empType, income);
            Db.update(c, "INSERT INTO application_documents (application_id, document_type, file_path) VALUES (?, ?, ?)",
                    appId, docType, docPath);
            ids[0] = uid;
            ids[1] = appId;
        });
        System.out.println("\nApplication #" + ids[1] + " submitted. Your account stays Disabled until an administrator approves it.");
    }
}
