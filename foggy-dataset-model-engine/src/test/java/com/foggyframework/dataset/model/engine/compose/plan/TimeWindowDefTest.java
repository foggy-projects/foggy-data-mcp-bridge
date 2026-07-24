package com.foggyframework.dataset.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TimeWindowDef}.
 *
 * @since 8.3.0.beta
 */
@DisplayName("TimeWindowDef")
class TimeWindowDefTest {

    // ==================================================================
    // Compact constructor validation
    // ==================================================================

    @Nested
    @DisplayName("Compact constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("null field → IllegalArgumentException")
        void nullField() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TimeWindowDef(null, "month", "yoy", "[)",
                            List.of("2024-01-01", "2025-01-01"), null, null));
        }

        @Test
        @DisplayName("blank field → IllegalArgumentException")
        void blankField() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TimeWindowDef("  ", "month", "yoy", "[)",
                            List.of("2024-01-01", "2025-01-01"), null, null));
        }

        @Test
        @DisplayName("null grain → IllegalArgumentException")
        void nullGrain() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TimeWindowDef("salesDate$id", null, "yoy", "[)",
                            List.of("2024-01-01", "2025-01-01"), null, null));
        }

        @Test
        @DisplayName("null comparison → IllegalArgumentException")
        void nullComparison() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TimeWindowDef("salesDate$id", "month", null, "[)",
                            List.of("2024-01-01", "2025-01-01"), null, null));
        }

        @Test
        @DisplayName("null range → defaults to [)")
        void nullRange() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", null,
                    List.of("2024-01-01", "2025-01-01"), null, null);
            assertEquals("[)", tw.range());
        }

        @Test
        @DisplayName("null value → empty list")
        void nullValue() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    null, null, null);
            assertNotNull(tw.value());
            assertTrue(tw.value().isEmpty());
        }

        @Test
        @DisplayName("null targetMetrics → stays null")
        void nullTargetMetrics() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"), null, null);
            assertNull(tw.targetMetrics());
        }

        @Test
        @DisplayName("value is immutable copy")
        void valueIsImmutable() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"), null, null);
            assertThrows(UnsupportedOperationException.class,
                    () -> tw.value().add("extra"));
        }
    }

    // ==================================================================
    // Classification helpers
    // ==================================================================

    @Nested
    @DisplayName("Classification helpers")
    class Classification {

        @Test
        @DisplayName("yoy is comparative")
        void yoyIsComparative() {
            TimeWindowDef tw = tw("yoy");
            assertTrue(tw.isComparative());
            assertFalse(tw.isCumulative());
            assertFalse(tw.isRolling());
        }

        @Test
        @DisplayName("mom is comparative")
        void momIsComparative() {
            assertTrue(tw("mom").isComparative());
        }

        @Test
        @DisplayName("wow is comparative")
        void wowIsComparative() {
            assertTrue(tw("wow").isComparative());
        }

        @Test
        @DisplayName("ytd is cumulative")
        void ytdIsCumulative() {
            TimeWindowDef tw = tw("ytd");
            assertFalse(tw.isComparative());
            assertTrue(tw.isCumulative());
            assertFalse(tw.isRolling());
        }

        @Test
        @DisplayName("mtd is cumulative")
        void mtdIsCumulative() {
            assertTrue(tw("mtd").isCumulative());
        }

        @Test
        @DisplayName("rolling_7d is rolling")
        void rolling7dIsRolling() {
            TimeWindowDef tw = tw("rolling_7d");
            assertFalse(tw.isComparative());
            assertFalse(tw.isCumulative());
            assertTrue(tw.isRolling());
        }

        @Test
        @DisplayName("rolling_30d is rolling")
        void rolling30dIsRolling() {
            assertTrue(tw("rolling_30d").isRolling());
        }

        @Test
        @DisplayName("rolling_90d is rolling")
        void rolling90dIsRolling() {
            assertTrue(tw("rolling_90d").isRolling());
        }

        private TimeWindowDef tw(String comparison) {
            return new TimeWindowDef("salesDate$id", "day", comparison, "[)",
                    List.of("-30D", "now"), null, null);
        }
    }

    // ==================================================================
    // rollingWindowSize()
    // ==================================================================

    @Nested
    @DisplayName("rollingWindowSize()")
    class RollingWindowSize {

        @Test
        @DisplayName("rolling_7d → 7")
        void rolling7d() {
            assertEquals(7, tw("rolling_7d").rollingWindowSize());
        }

        @Test
        @DisplayName("rolling_30d → 30")
        void rolling30d() {
            assertEquals(30, tw("rolling_30d").rollingWindowSize());
        }

        @Test
        @DisplayName("rolling_90d → 90")
        void rolling90d() {
            assertEquals(90, tw("rolling_90d").rollingWindowSize());
        }

        @Test
        @DisplayName("non-rolling comparison → throws")
        void nonRollingThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> tw("yoy").rollingWindowSize());
        }

        private TimeWindowDef tw(String comparison) {
            return new TimeWindowDef("salesDate$id", "day", comparison, "[)",
                    List.of("-30D", "now"), null, null);
        }
    }

    // ==================================================================
    // fromMap()
    // ==================================================================

    @Nested
    @DisplayName("fromMap()")
    class FromMap {

        @Test
        @DisplayName("null map → null")
        void nullMap() {
            assertNull(TimeWindowDef.fromMap(null));
        }

        @Test
        @DisplayName("minimal map with required fields")
        void minimalMap() {
            Map<String, Object> map = Map.of(
                    "field", "salesDate$id",
                    "grain", "month",
                    "comparison", "yoy"
            );
            TimeWindowDef tw = TimeWindowDef.fromMap(map);
            assertNotNull(tw);
            assertEquals("salesDate$id", tw.field());
            assertEquals("month", tw.grain());
            assertEquals("yoy", tw.comparison());
            assertEquals("[)", tw.range(), "default range");
            assertTrue(tw.value().isEmpty(), "empty value when no value key");
            assertNull(tw.targetMetrics(), "null targetMetrics when no key");
            assertNull(tw.rollingAggregator());
        }

        @Test
        @DisplayName("full map with all fields")
        void fullMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("field", "salesDate$id");
            map.put("grain", "day");
            map.put("comparison", "rolling_7d");
            map.put("range", "[]");
            map.put("value", List.of("-30D", "now"));
            map.put("targetMetrics", List.of("salesAmount", "costAmount"));
            map.put("rollingAggregator", "avg");

            TimeWindowDef tw = TimeWindowDef.fromMap(map);
            assertNotNull(tw);
            assertEquals("salesDate$id", tw.field());
            assertEquals("day", tw.grain());
            assertEquals("rolling_7d", tw.comparison());
            assertEquals("[]", tw.range());
            assertEquals(List.of("-30D", "now"), tw.value());
            assertEquals(List.of("salesAmount", "costAmount"), tw.targetMetrics());
            assertEquals("avg", tw.rollingAggregator());
        }

        @Test
        @DisplayName("value as non-list → null")
        void valueNonList() {
            Map<String, Object> map = new HashMap<>();
            map.put("field", "salesDate$id");
            map.put("grain", "month");
            map.put("comparison", "yoy");
            map.put("value", "not-a-list");

            TimeWindowDef tw = TimeWindowDef.fromMap(map);
            assertNotNull(tw);
            assertTrue(tw.value().isEmpty(), "non-list value → empty list");
        }
    }
}
