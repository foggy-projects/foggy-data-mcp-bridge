package com.foggyframework.dataset.model.cache.fingerprint;

import org.apache.commons.codec.digest.DigestUtils;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stable, type-aware and length-delimited value encoding for cache identities.
 *
 * <p>Unsupported mutable/domain objects are deliberately rejected instead of
 * falling back to {@code toString()}. That makes cache-key generation
 * fail-closed when a value cannot be represented without ambiguity.</p>
 */
public final class StableCanonicalEncoder {

    private static final int MAX_DEPTH = 32;

    private StableCanonicalEncoder() {
    }

    public static Optional<String> encode(Object value) {
        return encode(value, new IdentityHashMap<>(), 0);
    }

    public static String sha256(String value) {
        return DigestUtils.sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Appends a self-delimiting segment. Lengths make separators in values
     * irrelevant, for example {@code ["a,b", "c"]} cannot collide with
     * {@code ["a", "b,c"]}.
     */
    public static String segment(String type, String payload) {
        String safeType = type == null ? "" : type;
        String safePayload = payload == null ? "" : payload;
        return utf8Length(safeType) + ":" + safeType
                + utf8Length(safePayload) + ":" + safePayload;
    }

    private static Optional<String> encode(Object value,
                                           IdentityHashMap<Object, Boolean> visiting,
                                           int depth) {
        if (depth > MAX_DEPTH) {
            return Optional.empty();
        }
        if (value == null) {
            return Optional.of(segment("null", ""));
        }
        if (value instanceof String string) {
            return Optional.of(segment("string", string));
        }
        if (value instanceof Character character) {
            return Optional.of(segment("char", Integer.toString(character)));
        }
        if (value instanceof Boolean bool) {
            return Optional.of(segment("boolean", bool ? "1" : "0"));
        }
        if (value instanceof Byte number) {
            return Optional.of(segment("int8", number.toString()));
        }
        if (value instanceof Short number) {
            return Optional.of(segment("int16", number.toString()));
        }
        if (value instanceof Integer number) {
            return Optional.of(segment("int32", number.toString()));
        }
        if (value instanceof Long number) {
            return Optional.of(segment("int64", number.toString()));
        }
        if (value instanceof BigInteger number) {
            return Optional.of(segment("bigint", number.toString()));
        }
        if (value instanceof BigDecimal number) {
            String payload = segment("scale", Integer.toString(number.scale()))
                    + segment("value", number.toPlainString());
            return Optional.of(segment("decimal", payload));
        }
        if (value instanceof Float number) {
            return Optional.of(segment("float32", Integer.toUnsignedString(Float.floatToIntBits(number))));
        }
        if (value instanceof Double number) {
            return Optional.of(segment("float64", Long.toUnsignedString(Double.doubleToLongBits(number))));
        }
        if (value instanceof byte[] bytes) {
            return Optional.of(segment("bytes", Base64.getEncoder().encodeToString(bytes)));
        }
        if (value instanceof UUID uuid) {
            return Optional.of(segment("uuid", uuid.toString()));
        }
        if (value instanceof Enum<?> enumValue) {
            String payload = segment("class", enumValue.getDeclaringClass().getName())
                    + segment("name", enumValue.name());
            return Optional.of(segment("enum", payload));
        }
        if (value instanceof Class<?> type) {
            return Optional.of(segment("class", type.getName()));
        }
        if (value instanceof java.sql.Date date) {
            return Optional.of(segment("sql-date", date.toLocalDate().toString()));
        }
        if (value instanceof Time time) {
            return Optional.of(segment("sql-time", time.toLocalTime().toString()));
        }
        if (value instanceof Timestamp timestamp) {
            return Optional.of(segment("sql-timestamp", timestamp.toInstant().toString()));
        }
        if (value instanceof Date date) {
            return Optional.of(segment("date-millis", Long.toString(date.getTime())));
        }
        if (value instanceof Calendar calendar) {
            String payload = segment("millis", Long.toString(calendar.getTimeInMillis()))
                    + segment("zone", calendar.getTimeZone().getID());
            return Optional.of(segment("calendar", payload));
        }
        if (value instanceof Instant temporal) {
            return Optional.of(segment("instant", temporal.toString()));
        }
        if (value instanceof LocalDate temporal) {
            return Optional.of(segment("local-date", temporal.toString()));
        }
        if (value instanceof LocalTime temporal) {
            return Optional.of(segment("local-time", temporal.toString()));
        }
        if (value instanceof LocalDateTime temporal) {
            return Optional.of(segment("local-date-time", temporal.toString()));
        }
        if (value instanceof OffsetTime temporal) {
            return Optional.of(segment("offset-time", temporal.toString()));
        }
        if (value instanceof OffsetDateTime temporal) {
            return Optional.of(segment("offset-date-time", temporal.toString()));
        }
        if (value instanceof ZonedDateTime temporal) {
            return Optional.of(segment("zoned-date-time", temporal.toString()));
        }
        if (value instanceof ZoneId zoneId) {
            return Optional.of(segment("zone-id", zoneId.getId()));
        }
        if (value instanceof Duration duration) {
            return Optional.of(segment("duration", duration.toString()));
        }
        if (value instanceof Period period) {
            return Optional.of(segment("period", period.toString()));
        }
        if (value instanceof Year year) {
            return Optional.of(segment("year", year.toString()));
        }
        if (value instanceof YearMonth yearMonth) {
            return Optional.of(segment("year-month", yearMonth.toString()));
        }
        if (value instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
                return Optional.of(segment("optional", segment("empty", "")));
            }
            Optional<String> encoded = encode(optional.get(), visiting, depth + 1);
            return encoded.map(s -> segment("optional", segment("value", s)));
        }

        if (visiting.put(value, Boolean.TRUE) != null) {
            return Optional.empty();
        }
        try {
            Class<?> valueClass = value.getClass();
            if (valueClass.isArray()) {
                int length = Array.getLength(value);
                List<String> items = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    Optional<String> item = encode(Array.get(value, i), visiting, depth + 1);
                    if (item.isEmpty()) {
                        return Optional.empty();
                    }
                    items.add(item.get());
                }
                return Optional.of(encodeItems("array", items));
            }
            if (value instanceof Map<?, ?> map) {
                List<String> entries = new ArrayList<>(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Optional<String> key = encode(entry.getKey(), visiting, depth + 1);
                    Optional<String> itemValue = encode(entry.getValue(), visiting, depth + 1);
                    if (key.isEmpty() || itemValue.isEmpty()) {
                        return Optional.empty();
                    }
                    entries.add(segment("key", key.get()) + segment("value", itemValue.get()));
                }
                entries.sort(Comparator.naturalOrder());
                return Optional.of(encodeItems("map", entries));
            }
            if (value instanceof Collection<?> collection) {
                List<String> items = new ArrayList<>(collection.size());
                for (Object itemValue : collection) {
                    Optional<String> item = encode(itemValue, visiting, depth + 1);
                    if (item.isEmpty()) {
                        return Optional.empty();
                    }
                    items.add(item.get());
                }
                if (value instanceof Set<?>) {
                    items.sort(Comparator.naturalOrder());
                    return Optional.of(encodeItems("set", items));
                }
                return Optional.of(encodeItems("list", items));
            }
            if (value instanceof Iterable<?> iterable) {
                List<String> items = new ArrayList<>();
                for (Object itemValue : iterable) {
                    Optional<String> item = encode(itemValue, visiting, depth + 1);
                    if (item.isEmpty()) {
                        return Optional.empty();
                    }
                    items.add(item.get());
                }
                return Optional.of(encodeItems("iterable", items));
            }
        } finally {
            visiting.remove(value);
        }

        return Optional.empty();
    }

    private static String encodeItems(String type, List<String> items) {
        StringBuilder payload = new StringBuilder(segment("size", Integer.toString(items.size())));
        for (String item : items) {
            payload.append(segment("item", item));
        }
        return segment(type, payload.toString());
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
