package com.foggyframework.dataset.db.model.engine.compose.plan;

import java.util.Map;

/**
 * Structured representation of a SQL window frame clause.
 * e.g. ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
 * <p>
 * Using a structured record avoids SQL injection risks from raw string pass-through.
 *
 * @since 8.3.0.beta
 */
public record WindowFrame(
        Unit unit,
        Bound startBound,
        Bound endBound
) {

    public enum Unit {
        ROWS, RANGE
    }

    /**
     * Represents a window frame boundary.
     */
    public interface Bound {
        record UnboundedPreceding() implements Bound {}
        record Preceding(int offset) implements Bound {
            public Preceding {
                if (offset < 0) throw new IllegalArgumentException("Preceding offset must be >= 0, got: " + offset);
            }
        }
        record CurrentRow() implements Bound {}
        record Following(int offset) implements Bound {
            public Following {
                if (offset < 0) throw new IllegalArgumentException("Following offset must be >= 0, got: " + offset);
            }
        }
        record UnboundedFollowing() implements Bound {}
    }

    // ---- Convenience factories ----

    /**
     * ROWS BETWEEN {n-1} PRECEDING AND CURRENT ROW — for rolling windows.
     */
    public static WindowFrame rollingRows(int windowSize) {
        return new WindowFrame(Unit.ROWS, new Bound.Preceding(windowSize - 1), new Bound.CurrentRow());
    }

    /**
     * ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW — for cumulative aggregation.
     */
    public static WindowFrame cumulativeRows() {
        return new WindowFrame(Unit.ROWS, new Bound.UnboundedPreceding(), new Bound.CurrentRow());
    }

    // ---- SQL rendering ----

    /**
     * Renders the frame clause to SQL. The syntax is standard across most dialects.
     * e.g. "ROWS BETWEEN 6 PRECEDING AND CURRENT ROW"
     */
    public String toSql() {
        return unit.name() + " BETWEEN " + boundToSql(startBound) + " AND " + boundToSql(endBound);
    }

    private static String boundToSql(Bound bound) {
        if (bound instanceof Bound.UnboundedPreceding) return "UNBOUNDED PRECEDING";
        if (bound instanceof Bound.Preceding p) return p.offset() + " PRECEDING";
        if (bound instanceof Bound.CurrentRow) return "CURRENT ROW";
        if (bound instanceof Bound.Following f) return f.offset() + " FOLLOWING";
        if (bound instanceof Bound.UnboundedFollowing) return "UNBOUNDED FOLLOWING";
        throw new IllegalArgumentException("Unknown bound type: " + bound.getClass().getSimpleName());
    }

    // ---- Parsing from Map (JS sandbox interop) ----

    /**
     * Parse from a structured map, e.g. {"unit": "rows", "start": -6, "end": 0}
     * or from a raw string like "ROWS BETWEEN 6 PRECEDING AND CURRENT ROW".
     */
    @SuppressWarnings("unchecked")
    public static WindowFrame fromMapOrString(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String s) {
            return parseString(s);
        }
        if (raw instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            String unitStr = String.valueOf(map.getOrDefault("unit", "ROWS")).toUpperCase();
            Unit unit = Unit.valueOf(unitStr);
            int start = ((Number) map.getOrDefault("start", 0)).intValue();
            int end = ((Number) map.getOrDefault("end", 0)).intValue();
            return new WindowFrame(unit, intToBound(start), intToBound(end));
        }
        throw new IllegalArgumentException("Cannot parse WindowFrame from: " + raw.getClass().getSimpleName());
    }

    private static Bound intToBound(int val) {
        if (val == Integer.MIN_VALUE) return new Bound.UnboundedPreceding();
        if (val == Integer.MAX_VALUE) return new Bound.UnboundedFollowing();
        if (val < 0) return new Bound.Preceding(-val);
        if (val == 0) return new Bound.CurrentRow();
        return new Bound.Following(val);
    }

    private static WindowFrame parseString(String s) {
        // Simple parser for "ROWS BETWEEN 6 PRECEDING AND CURRENT ROW" style strings
        String upper = s.trim().toUpperCase();
        Unit unit = upper.startsWith("RANGE") ? Unit.RANGE : Unit.ROWS;

        int betweenIdx = upper.indexOf("BETWEEN");
        int andIdx = upper.indexOf(" AND ", betweenIdx);
        if (betweenIdx < 0 || andIdx < 0) {
            throw new IllegalArgumentException("Cannot parse WindowFrame string: " + s);
        }

        String startStr = upper.substring(betweenIdx + 7, andIdx).trim();
        String endStr = upper.substring(andIdx + 5).trim();

        return new WindowFrame(unit, parseBound(startStr), parseBound(endStr));
    }

    private static Bound parseBound(String s) {
        if (s.equals("CURRENT ROW")) return new Bound.CurrentRow();
        if (s.equals("UNBOUNDED PRECEDING")) return new Bound.UnboundedPreceding();
        if (s.equals("UNBOUNDED FOLLOWING")) return new Bound.UnboundedFollowing();
        if (s.endsWith("PRECEDING")) {
            int n = Integer.parseInt(s.replace("PRECEDING", "").trim());
            return new Bound.Preceding(n);
        }
        if (s.endsWith("FOLLOWING")) {
            int n = Integer.parseInt(s.replace("FOLLOWING", "").trim());
            return new Bound.Following(n);
        }
        throw new IllegalArgumentException("Cannot parse bound: " + s);
    }
}
