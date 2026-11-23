package loansavings;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Pattern;

/** Console input/output helpers with validation. */
final class Cli {
    private static final Scanner IN = new Scanner(System.in);
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private Cli() {}

    /** Thrown when standard input ends (Ctrl+D / Ctrl+Z). */
    static final class EndOfInput extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @FunctionalInterface
    interface Action { void run() throws Exception; }

    /** Run an action; show errors instead of crashing the menu loop. */
    static void safe(Action a) {
        try {
            a.run();
        } catch (EndOfInput e) {
            throw e;
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    static String line() {
        if (!IN.hasNextLine()) throw new EndOfInput();
        return IN.nextLine().trim();
    }

    // ---------- output ----------
    static String money(double v) { return String.format("%,.2f", v); }

    static void title(String text) {
        System.out.println("\n" + "=".repeat(62));
        System.out.println(" " + text);
        System.out.println("=".repeat(62));
    }

    static void pause() {
        System.out.print("\nPress Enter to continue...");
        line();
    }

    /** Print rows as an aligned table; keys select columns from each Row. */
    static void table(List<Row> rows, String[] headers, String[] keys) {
        if (rows.isEmpty()) {
            System.out.println("  (no records)");
            return;
        }
        int[] w = new int[headers.length];
        for (int i = 0; i < w.length; i++) {
            w[i] = headers[i].length();
            for (Row r : rows) w[i] = Math.max(w[i], r.fmt(keys[i]).length());
        }
        StringBuilder head = new StringBuilder(), sep = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            head.append(String.format("%-" + w[i] + "s  ", headers[i]));
            sep.append("-".repeat(w[i])).append("  ");
        }
        System.out.println(head);
        System.out.println(sep);
        for (Row r : rows) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < w.length; i++) sb.append(String.format("%-" + w[i] + "s  ", r.fmt(keys[i])));
            System.out.println(sb);
        }
    }

    static int menu(String heading, String... options) {
        title(heading);
        for (int i = 0; i < options.length; i++) System.out.printf(" [%d] %s%n", i + 1, options[i]);
        System.out.println(" [0] Back / Logout");
        while (true) {
            System.out.print("Choose: ");
            String s = line();
            if (s.matches("\\d+") && Integer.parseInt(s) <= options.length) return Integer.parseInt(s);
            System.out.println("Invalid choice.");
        }
    }

    // ---------- validated input ----------
    static String ask(String prompt) {
        while (true) {
            System.out.print(prompt + ": ");
            String s = line();
            if (!s.isEmpty() && s.length() <= 255) return s;
            System.out.println("  Input required (max 255 characters).");
        }
    }

    static int askInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt + ": ");
            try {
                int n = Integer.parseInt(line());
                if (n >= min && n <= max) return n;
            } catch (NumberFormatException ignored) { }
            System.out.printf("  Enter a whole number between %d and %d.%n", min, max);
        }
    }

    static double askMoney(String prompt, double min, double max) {
        while (true) {
            System.out.print(prompt + ": ");
            try {
                double n = Math.round(Double.parseDouble(line().replace(",", "")) * 100.0) / 100.0;
                if (n >= min && n <= max) return n;
            } catch (NumberFormatException ignored) { }
            System.out.printf("  Enter an amount between %s and %s.%n", money(min), money(max));
        }
    }

    static String askChoice(String prompt, String... choices) {
        String label = String.join("/", choices);
        while (true) {
            System.out.print(prompt + " (" + label + "): ");
            String s = line().toLowerCase(Locale.ROOT);
            for (String c : choices) if (c.toLowerCase(Locale.ROOT).equals(s)) return c;
            System.out.println("  Choose one of: " + label);
        }
    }

    static String askDate(String prompt) {
        while (true) {
            System.out.print(prompt + " (YYYY-MM-DD): ");
            try {
                return LocalDate.parse(line()).toString();
            } catch (DateTimeParseException e) {
                System.out.println("  Invalid date.");
            }
        }
    }

    static String askEmail(String prompt) {
        while (true) {
            System.out.print(prompt + ": ");
            String s = line();
            if (EMAIL.matcher(s).matches()) return s;
            System.out.println("  Invalid email address.");
        }
    }

    static boolean confirm(String prompt) {
        System.out.print(prompt + " (y/n): ");
        return line().equalsIgnoreCase("y");
    }

    /** Hidden input when a real terminal is available, plain input otherwise. */
    static String askPassword(String prompt) {
        var console = System.console();
        if (console != null) {
            char[] pw = console.readPassword(prompt + ": ");
            if (pw == null) throw new EndOfInput();
            return new String(pw);
        }
        System.out.print(prompt + ": ");
        return line();
    }
}
