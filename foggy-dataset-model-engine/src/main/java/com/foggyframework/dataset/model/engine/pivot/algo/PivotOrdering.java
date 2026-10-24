package com.foggyframework.dataset.model.engine.pivot.algo;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Pivot member order: NULL last in either direction, with exact numeric comparison. */
public final class PivotOrdering {
    private PivotOrdering() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static int compare(Object a, Object b, boolean descending) {
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        int result;
        if (a instanceof Number && b instanceof Number)
            result = new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
        else if (a instanceof Comparable && a.getClass().isInstance(b))
            result = ((Comparable) a).compareTo(b);
        else result = a.toString().compareTo(b.toString());
        return descending ? -Integer.signum(result) : result;
    }

    public static Comparator<Map<String, Object>> comparator(List<String> orders) {
        return (a, b) -> {
            for (String order : orders) {
                boolean descending = order.startsWith("-");
                String field = descending ? order.substring(1) : order;
                int result = compare(a.get(field), b.get(field), descending);
                if (result != 0) return result;
            }
            return 0;
        };
    }
}
