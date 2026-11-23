package loansavings;

import java.util.LinkedHashMap;
import java.util.Map;

/** One database result row (column label -> value). */
final class Row {
    private final Map<String, Object> values = new LinkedHashMap<>();

    void put(String key, Object value) { values.put(key, value); }

    boolean isNull(String key) { return values.get(key) == null; }

    String str(String key) {
        Object v = values.get(key);
        return v == null ? "" : v.toString();
    }

    long lng(String key) {
        Object v = values.get(key);
        if (v == null) return 0L;
        return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString());
    }

    int intVal(String key) { return (int) lng(key); }

    double dbl(String key) {
        Object v = values.get(key);
        if (v == null) return 0.0;
        return v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString());
    }

    /** Display form: decimals get 2 places and thousands separators. */
    String fmt(String key) {
        Object v = values.get(key);
        if (v instanceof Double || v instanceof Float) return String.format("%,.2f", ((Number) v).doubleValue());
        return str(key);
    }
}
