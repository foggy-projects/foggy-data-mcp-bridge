package com.foggyframework.dataset.db.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RelativeDateParser}.
 *
 * @since 8.3.0.beta
 */
@DisplayName("RelativeDateParser")
class RelativeDateParserTest {

    // ==================================================================
    // isValid()
    // ==================================================================

    @Nested
    @DisplayName("isValid()")
    class IsValid {

        @Test void now() { assertTrue(RelativeDateParser.isValid("now")); }
        @Test void nowUpperCase() { assertTrue(RelativeDateParser.isValid("NOW")); }
        @Test void minus1Y() { assertTrue(RelativeDateParser.isValid("-1Y")); }
        @Test void minus7D() { assertTrue(RelativeDateParser.isValid("-7D")); }
        @Test void minus1M() { assertTrue(RelativeDateParser.isValid("-1M")); }
        @Test void minus1W() { assertTrue(RelativeDateParser.isValid("-1W")); }
        @Test void minus1Q() { assertTrue(RelativeDateParser.isValid("-1Q")); }
        @Test void isoDate() { assertTrue(RelativeDateParser.isValid("2024-01-01")); }
        @Test void compactDate() { assertTrue(RelativeDateParser.isValid("20240101")); }
        @Test void empty() { assertFalse(RelativeDateParser.isValid("")); }
        @Test void nullStr() { assertFalse(RelativeDateParser.isValid(null)); }
        @Test void garbage() { assertFalse(RelativeDateParser.isValid("not-a-date")); }
        @Test void partialDate() { assertFalse(RelativeDateParser.isValid("2024-13-01")); }
    }

    // ==================================================================
    // parse() — "now"
    // ==================================================================

    @Nested
    @DisplayName("parse(\"now\")")
    class ParseNow {

        @Test
        @DisplayName("now → NOW type")
        void parseNow() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("now");
            assertEquals(RelativeDateParser.DateExpr.Type.NOW, expr.type());
            assertTrue(expr.isNow());
        }
    }

    // ==================================================================
    // parse() — relative
    // ==================================================================

    @Nested
    @DisplayName("parse(relative)")
    class ParseRelative {

        @Test
        @DisplayName("-1Y → YEAR, -1")
        void minus1Y() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("-1Y");
            assertEquals(RelativeDateParser.DateExpr.Type.RELATIVE, expr.type());
            assertEquals(RelativeDateParser.OffsetUnit.YEAR, expr.offsetUnit());
            assertEquals(-1, expr.offsetAmount());
        }

        @Test
        @DisplayName("-7D → DAY, -7")
        void minus7D() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("-7D");
            assertEquals(RelativeDateParser.OffsetUnit.DAY, expr.offsetUnit());
            assertEquals(-7, expr.offsetAmount());
        }

        @Test
        @DisplayName("-1M → MONTH, -1")
        void minus1M() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("-1M");
            assertEquals(RelativeDateParser.OffsetUnit.MONTH, expr.offsetUnit());
            assertEquals(-1, expr.offsetAmount());
        }

        @Test
        @DisplayName("-1W → WEEK, -1")
        void minus1W() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("-1W");
            assertEquals(RelativeDateParser.OffsetUnit.WEEK, expr.offsetUnit());
            assertEquals(-1, expr.offsetAmount());
        }

        @Test
        @DisplayName("-1Q → QUARTER, -1")
        void minus1Q() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("-1Q");
            assertEquals(RelativeDateParser.OffsetUnit.QUARTER, expr.offsetUnit());
            assertEquals(-1, expr.offsetAmount());
        }

        // ---- Sign-direction edge cases (BUG-J1 regression guard) ----

        @Test
        @DisplayName("30D (unsigned) → DAY, +30 (future, not past)")
        void unsigned30D() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("30D");
            assertEquals(RelativeDateParser.OffsetUnit.DAY, expr.offsetUnit());
            assertEquals(30, expr.offsetAmount(),
                    "Unsigned '30D' must be positive (future). " +
                    "BUG-J1 caused this to be -30 (past).");
        }

        @Test
        @DisplayName("+30D → DAY, +30")
        void plus30D() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("+30D");
            assertEquals(RelativeDateParser.OffsetUnit.DAY, expr.offsetUnit());
            assertEquals(30, expr.offsetAmount());
        }

        @Test
        @DisplayName("-30D → DAY, -30")
        void minus30D() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("-30D");
            assertEquals(RelativeDateParser.OffsetUnit.DAY, expr.offsetUnit());
            assertEquals(-30, expr.offsetAmount());
        }

        @Test
        @DisplayName("1Y (unsigned) → YEAR, +1")
        void unsigned1Y() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("1Y");
            assertEquals(RelativeDateParser.OffsetUnit.YEAR, expr.offsetUnit());
            assertEquals(1, expr.offsetAmount());
        }

        @Test
        @DisplayName("2W (unsigned) → WEEK, +2")
        void unsigned2W() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("2W");
            assertEquals(RelativeDateParser.OffsetUnit.WEEK, expr.offsetUnit());
            assertEquals(2, expr.offsetAmount());
        }

        @Test
        @DisplayName("3M (unsigned) → MONTH, +3")
        void unsigned3M() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("3M");
            assertEquals(RelativeDateParser.OffsetUnit.MONTH, expr.offsetUnit());
            assertEquals(3, expr.offsetAmount());
        }
    }

    // ==================================================================
    // parse() — absolute
    // ==================================================================

    @Nested
    @DisplayName("parse(absolute)")
    class ParseAbsolute {

        @Test
        @DisplayName("2024-01-01 → ABSOLUTE, date")
        void isoFormat() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("2024-01-01");
            assertEquals(RelativeDateParser.DateExpr.Type.ABSOLUTE, expr.type());
            assertEquals(LocalDate.of(2024, 1, 1), expr.absoluteDate());
        }

        @Test
        @DisplayName("20240615 → ABSOLUTE, date")
        void compactFormat() {
            RelativeDateParser.DateExpr expr = RelativeDateParser.parse("20240615");
            assertEquals(RelativeDateParser.DateExpr.Type.ABSOLUTE, expr.type());
            assertEquals(LocalDate.of(2024, 6, 15), expr.absoluteDate());
        }
    }

    // ==================================================================
    // parse() — error cases
    // ==================================================================

    @Nested
    @DisplayName("parse() errors")
    class ParseErrors {

        @Test
        @DisplayName("null → throws")
        void nullInput() {
            assertThrows(IllegalArgumentException.class, () -> RelativeDateParser.parse(null));
        }

        @Test
        @DisplayName("empty → throws")
        void emptyInput() {
            assertThrows(IllegalArgumentException.class, () -> RelativeDateParser.parse(""));
        }

        @Test
        @DisplayName("garbage → throws")
        void garbageInput() {
            assertThrows(IllegalArgumentException.class, () -> RelativeDateParser.parse("not-a-date"));
        }
    }
}
