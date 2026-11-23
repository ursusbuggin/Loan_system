package loansavings;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

/** Entry point: Loan and Savings Management System (console). */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        try {
            if (Arrays.asList(args).contains("--reset") || !Db.exists()) {
                Db.init(true);
                System.out.println("Database created with sample data.");
            }
            try (Connection c = Db.connect()) {
                loop(c);
            }
        } catch (Cli.EndOfInput e) {
            System.out.println("\nGoodbye.");
        } catch (Exception e) {
            System.err.println("Fatal error: " + e);
            System.exit(1);
        }
    }

    private static void loop(Connection c) {
        while (true) {
            int choice = Cli.menu("LOAN AND SAVINGS MANAGEMENT SYSTEM",
                    "Member login", "Administrator login", "Register as a new member", "Reset demo database");
            if (choice == 0) {
                System.out.println("Goodbye.");
                return;
            }
            Cli.safe(() -> {
                switch (choice) {
                    case 1 -> memberLogin(c);
                    case 2 -> adminLogin(c);
                    case 3 -> Registration.run(c);
                    default -> reset(c);
                }
            });
        }
    }

    private static void memberLogin(Connection c) throws SQLException {
        String name = Cli.ask("Username");
        String pw = Cli.askPassword("Password");
        Row u = Db.one(c, "SELECT user_id, username, password_hash, account_status FROM users WHERE username = ?", name);
        if (u == null || !Security.verify(pw, u.str("password_hash"))) {
            System.out.println("Invalid username or password.");
            return;
        }
        if (!u.str("account_status").equals("Active")) {
            System.out.println("This account is Disabled (pending approval or suspended).");
            return;
        }
        new UserMenu(c, u.lng("user_id"), u.str("username")).run();
    }

    private static void adminLogin(Connection c) throws SQLException {
        String name = Cli.ask("Admin username");
        String pw = Cli.askPassword("Password");
        Row a = Db.one(c, "SELECT admin_id, username, password_hash, status FROM administrators WHERE username = ?", name);
        if (a == null || !Security.verify(pw, a.str("password_hash"))) {
            System.out.println("Invalid username or password.");
            return;
        }
        if (!a.str("status").equals("Active")) {
            System.out.println("This administrator account is disabled.");
            return;
        }
        new AdminMenu(c, a.lng("admin_id"), a.str("username")).run();
    }

    private static void reset(Connection c) throws Exception {
        if (!Cli.confirm("This erases ALL data and reloads the sample data. Continue")) return;
        c.close();
        Db.init(true);
        System.out.println("Database reset. Restart the program to continue.");
        throw new Cli.EndOfInput();
    }
}
