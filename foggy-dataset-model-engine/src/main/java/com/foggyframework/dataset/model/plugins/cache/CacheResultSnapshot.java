package com.foggyframework.dataset.model.plugins.cache;

import com.foggyframework.dataset.model.PagingResultImpl;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Creates an isolated structural snapshot at the cache boundary.
 *
 * <p>Query results are mutable and local cache providers may retain object references. Only
 * explicitly supported value types are copied; cycles, excessive nesting and opaque values are
 * rejected so callers can skip caching instead of sharing an unsafe result reference.</p>
 */
public final class CacheResultSnapshot {

    private static final int MAX_DEPTH = 64;

    private CacheResultSnapshot() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static PagingResultImpl copy(PagingResultImpl source) {
        if (source == null) {
            return null;
        }

        IdentityHashMap<Object, Boolean> activePath = new IdentityHashMap<>();
        PagingResultImpl copy = new PagingResultImpl();
        copy.setTotal(source.getTotal());
        copy.setHasNext(source.isHasNext());
        copy.setStart(source.getStart());
        copy.setLimit(source.getLimit());
        copy.setItems((List) copyValue(source.getItems(), activePath, 0));
        copy.setTotalData(copyValue(source.getTotalData(), activePath, 0));
        return copy;
    }

    private static Object copyValue(Object value,
                                    IdentityHashMap<Object, Boolean> activePath,
                                    int depth) {
        if (value == null || isKnownImmutable(value)) {
            return value;
        }
        if (depth >= MAX_DEPTH) {
            throw unsafe("cache value nesting exceeds " + MAX_DEPTH, value);
        }
        if (value instanceof Timestamp timestamp) {
            Timestamp copy = new Timestamp(timestamp.getTime());
            copy.setNanos(timestamp.getNanos());
            return copy;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return new java.sql.Date(sqlDate.getTime());
        }
        if (value instanceof Time time) {
            return new Time(time.getTime());
        }
        if (value instanceof Date date) {
            return new Date(date.getTime());
        }
        if (value instanceof Calendar calendar) {
            return calendar.clone();
        }

        enter(value, activePath);
        try {
            if (value instanceof Map<?, ?> map) {
                Map<Object, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copy.put(copyValue(entry.getKey(), activePath, depth + 1),
                            copyValue(entry.getValue(), activePath, depth + 1));
                }
                return copy;
            }
            if (value instanceof List<?> list) {
                List<Object> copy = new ArrayList<>(list.size());
                for (Object item : list) {
                    copy.add(copyValue(item, activePath, depth + 1));
                }
                return copy;
            }
            if (value instanceof Set<?> set) {
                Set<Object> copy = new LinkedHashSet<>();
                for (Object item : set) {
                    copy.add(copyValue(item, activePath, depth + 1));
                }
                return copy;
            }
            if (value instanceof Collection<?> collection) {
                List<Object> copy = new ArrayList<>(collection.size());
                for (Object item : collection) {
                    copy.add(copyValue(item, activePath, depth + 1));
                }
                return copy;
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                Object copy = Array.newInstance(value.getClass().getComponentType(), length);
                for (int i = 0; i < length; i++) {
                    Array.set(copy, i, copyValue(Array.get(value, i), activePath, depth + 1));
                }
                return copy;
            }
        } finally {
            activePath.remove(value);
        }

        throw unsafe("unsupported cache value type", value);
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> activePath) {
        if (activePath.put(value, Boolean.TRUE) != null) {
            throw unsafe("cyclic cache value", value);
        }
    }

    private static boolean isKnownImmutable(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal
                || value instanceof UUID
                || value instanceof Enum<?>
                || value.getClass().getPackageName().equals("java.time");
    }

    private static UnsafeCacheValueException unsafe(String reason, Object value) {
        return new UnsafeCacheValueException(reason + ": " + value.getClass().getName());
    }

    public static final class UnsafeCacheValueException extends RuntimeException {
        private UnsafeCacheValueException(String message) {
            super(message);
        }
    }
}
