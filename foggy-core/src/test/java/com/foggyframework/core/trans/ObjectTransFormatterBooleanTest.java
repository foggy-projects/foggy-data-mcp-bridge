package com.foggyframework.core.trans;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ObjectTransFormatter.BooleanTransFormatter#format(Object)}.
 * Covers Number sub-types, String, Boolean, and null inputs.
 */
class ObjectTransFormatterBooleanTest {

    private final ObjectTransFormatter.BooleanTransFormatter formatter =
            ObjectTransFormatter.BOOLEAN_TRANSFORMATTERINSTANCE;

    // ──────────────────────────────────────────────
    // Integer
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("Integer inputs")
    class IntegerTests {
        @Test void zero()     { assertFalse(formatter.format(0)); }
        @Test void positive() { assertTrue(formatter.format(1)); }
        @Test void negative() { assertTrue(formatter.format(-1)); }
    }

    // ──────────────────────────────────────────────
    // Long
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("Long inputs")
    class LongTests {
        @Test void zero()    { assertFalse(formatter.format(0L)); }
        @Test void maxValue() { assertTrue(formatter.format(Long.MAX_VALUE)); }
        @Test void minValue() { assertTrue(formatter.format(Long.MIN_VALUE)); }
    }

    // ──────────────────────────────────────────────
    // Double
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("Double inputs")
    class DoubleTests {
        @Test void zero()        { assertFalse(formatter.format(0.0)); }
        @Test void tinyPositive() { assertTrue(formatter.format(0.0000001)); }
        @Test void negative()    { assertTrue(formatter.format(-0.5)); }

        @Test
        @DisplayName("Double.NaN should be true (non-zero)")
        void nan() {
            // NaN != 0 is true in Java, so NaN maps to true
            assertTrue(formatter.format(Double.NaN));
        }
    }

    // ──────────────────────────────────────────────
    // Float
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("Float inputs")
    class FloatTests {
        @Test void zero()    { assertFalse(formatter.format(0.0f)); }
        @Test void nonZero() { assertTrue(formatter.format(1.0f)); }
    }

    // ──────────────────────────────────────────────
    // BigDecimal
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("BigDecimal inputs")
    class BigDecimalTests {
        @Test void zero()        { assertFalse(formatter.format(BigDecimal.ZERO)); }
        @Test void tinyPositive() { assertTrue(formatter.format(new BigDecimal("0.0000001"))); }
        @Test void one()         { assertTrue(formatter.format(BigDecimal.ONE)); }

        @Test
        @DisplayName("BigDecimal '0.0' (different scale) should still be false")
        void zeroWithScale() {
            assertFalse(formatter.format(new BigDecimal("0.0")));
        }
    }

    // ──────────────────────────────────────────────
    // BigInteger
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("BigInteger inputs")
    class BigIntegerTests {
        @Test void zero() { assertFalse(formatter.format(BigInteger.ZERO)); }
        @Test void one()  { assertTrue(formatter.format(BigInteger.ONE)); }
    }

    // ──────────────────────────────────────────────
    // Short
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("Short inputs")
    class ShortTests {
        @Test void zero()    { assertFalse(formatter.format((short) 0)); }
        @Test void nonZero() { assertTrue(formatter.format((short) 1)); }
    }

    // ──────────────────────────────────────────────
    // Byte
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("Byte inputs")
    class ByteTests {
        @Test void zero()    { assertFalse(formatter.format((byte) 0)); }
        @Test void nonZero() { assertTrue(formatter.format((byte) 1)); }
    }

    // ──────────────────────────────────────────────
    // String inputs
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("String inputs")
    class StringTests {
        @Test void trueString()  { assertTrue(formatter.format("true")); }
        @Test void falseString() { assertFalse(formatter.format("false")); }
        @Test void zeroString()  { assertFalse(formatter.format("0")); }
        @Test void oneString()   { assertTrue(formatter.format("1")); }
        @Test void emptyString() { assertFalse(formatter.format("")); }
    }

    // ──────────────────────────────────────────────
    // Boolean inputs
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("Boolean inputs")
    class BooleanTests {
        @Test void trueValue()  { assertTrue(formatter.format(Boolean.TRUE)); }
        @Test void falseValue() { assertFalse(formatter.format(Boolean.FALSE)); }
    }

    // ──────────────────────────────────────────────
    // null input
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("null input should return null")
    void nullInput() {
        assertNull(formatter.format(null));
    }
}
