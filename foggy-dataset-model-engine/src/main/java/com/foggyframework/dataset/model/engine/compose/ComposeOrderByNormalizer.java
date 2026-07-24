package com.foggyframework.dataset.model.engine.compose;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;

import java.util.Locale;
import java.util.Map;

/**
 * Shared orderBy shorthand normalization for query_model parity.
 *
 * <p>Compose base DSL, derived/CTE rendering, and legacy direct DSL entry
 * points all accept the same compact forms: {@code -field}, {@code +field},
 * bare {@code field}, {@code field desc/asc}, and {@code field:desc/asc}.</p>
 */
public final class ComposeOrderByNormalizer {

    private ComposeOrderByNormalizer() { /* utility */ }

    public record OrderSpec(String field, String dir) {
        public String dirUpper() {
            return dir.toUpperCase(Locale.ROOT);
        }
    }

    public static OrderSpec parse(String entry) {
        if (entry == null) {
            return new OrderSpec("", "asc");
        }
        String text = entry.trim();
        String dir = "asc";
        if (text.startsWith("-")) {
            dir = "desc";
            text = text.substring(1).trim();
        } else if (text.startsWith("+")) {
            text = text.substring(1).trim();
        }

        int colon = text.indexOf(':');
        if (colon >= 0) {
            String field = text.substring(0, colon).trim();
            String explicitDir = text.substring(colon + 1).trim();
            return new OrderSpec(field, normalizeDir(explicitDir, dir));
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" desc")) {
            return new OrderSpec(text.substring(0, text.length() - 5).trim(), "desc");
        }
        if (lower.endsWith(" asc")) {
            return new OrderSpec(text.substring(0, text.length() - 4).trim(), "asc");
        }
        return new OrderSpec(text, dir);
    }

    public static OrderSpec parse(Object raw) {
        if (raw instanceof String str) {
            return parse(str);
        }
        if (raw instanceof SemanticQueryRequest.OrderItem item) {
            return parseFieldAndDir(item.getField(), item.getDir());
        }
        if (raw instanceof Map<?, ?> map) {
            Object field = firstNonNull(map.get("field"), map.get("fieldName"), map.get("column"));
            Object dir = firstNonNull(map.get("dir"), map.get("direction"), map.get("order"));
            return parseFieldAndDir(field, dir);
        }
        throw new IllegalArgumentException(
                "orderBy entries must be String, Map, or OrderItem, got "
                        + (raw == null ? "null" : raw.getClass().getSimpleName()));
    }

    public static SemanticQueryRequest.OrderItem toOrderItem(String entry) {
        return toOrderItem(parse(entry));
    }

    public static SemanticQueryRequest.OrderItem toOrderItem(Object raw) {
        return toOrderItem(parse(raw));
    }

    public static String fieldName(String entry) {
        return parse(entry).field();
    }

    private static OrderSpec parseFieldAndDir(Object field, Object explicitDir) {
        if (!(field instanceof String fieldText)) {
            throw new IllegalArgumentException("orderBy object entry must include a string field");
        }
        OrderSpec parsed = parse(fieldText);
        return new OrderSpec(parsed.field(), normalizeDir(explicitDir, parsed.dir()));
    }

    private static SemanticQueryRequest.OrderItem toOrderItem(OrderSpec spec) {
        SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
        item.setField(spec.field());
        item.setDir(spec.dir());
        return item;
    }

    private static Object firstNonNull(Object first, Object second, Object third) {
        if (first != null) return first;
        if (second != null) return second;
        return third;
    }

    private static String normalizeDir(Object raw, String defaultDir) {
        String dir = raw == null ? defaultDir : raw.toString().trim().toLowerCase(Locale.ROOT);
        if ("asc".equals(dir) || "desc".equals(dir)) {
            return dir;
        }
        return ("asc".equals(defaultDir) || "desc".equals(defaultDir)) ? defaultDir : "asc";
    }
}
