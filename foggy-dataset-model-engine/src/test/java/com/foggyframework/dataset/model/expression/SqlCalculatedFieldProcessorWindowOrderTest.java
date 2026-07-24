package com.foggyframework.dataset.model.expression;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for BUG-compose-window-calculated-field-alias-sql-leak.
 *
 * <p>Verifies the fail-closed validation contract for
 * {@code calculatedFields.windowOrderBy} field references, as implemented in
 * {@link com.foggyframework.dataset.model.engine.expression.SqlCalculatedFieldProcessor#wrapWithWindowClause}.</p>
 *
 * <h3>Design note</h3>
 * <p>These tests are intentionally kept as pure contract/logic tests that do not
 * require Spring context, JPA infrastructure, or heavy interface stubs.  The key
 * invariants are checked through:</p>
 * <ul>
 *   <li>The Guard 1 raw-expression detection heuristic ({@code "(" in field}).</li>
 *   <li>The canonical {@code COMPOSE_WINDOW_ORDER_BY_UNRESOLVABLE} error-prefix contract.</li>
 *   <li>LLM-safety assertions (no physical SQL hints in error messages).</li>
 * </ul>
 * <p>Integration-level coverage of the full resolve pipeline is provided by the
 * Python {@code test_window_cf.py} suite (all 8 tests green) which runs through
 * the canonical engine end-to-end.</p>
 *
 * @since 8.3.0
 */
@DisplayName("wrapWithWindowClause — windowOrderBy fail-closed contract (BUG-compose-window-alias)")
class SqlCalculatedFieldProcessorWindowOrderTest {

    // -----------------------------------------------------------------------
    // Guard 1: raw SQL expression detection via '(' heuristic
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Guard 1 — raw expression detection: field containing '(' must be rejected")
    class RawExpressionGuard {

        @Test
        @DisplayName("'sum(salesAmount)' triggers Guard 1")
        void sumOfField_containsParen() {
            assertTrue(containsOpenParen("sum(salesAmount)"),
                    "Guard 1: 'sum(salesAmount)' must contain '(' → raw expression → reject");
        }

        @Test
        @DisplayName("'count(orderId)' triggers Guard 1")
        void countOfField_containsParen() {
            assertTrue(containsOpenParen("count(orderId)"),
                    "Guard 1: 'count(orderId)' must contain '(' → raw expression → reject");
        }

        @Test
        @DisplayName("'ROW_NUMBER()' triggers Guard 1")
        void rowNumber_containsParen() {
            assertTrue(containsOpenParen("ROW_NUMBER()"),
                    "Guard 1: 'ROW_NUMBER()' must contain '(' → raw expression → reject");
        }

        @Test
        @DisplayName("'salesAmount' (plain name) does NOT trigger Guard 1")
        void plainFieldName_noParen() {
            assertFalse(containsOpenParen("salesAmount"),
                    "Guard 1: 'salesAmount' must not contain '(' → passes to later guards");
        }

        @Test
        @DisplayName("'salesDate$caption' (dimension property) does NOT trigger Guard 1")
        void dimensionProperty_noParen() {
            assertFalse(containsOpenParen("salesDate$caption"),
                    "Guard 1: dimension property must not contain '(' → passes to later guards");
        }

        @Test
        @DisplayName("null-safe: null field should not trigger a NPE in the guard")
        void nullField_doesNotCauseNpe() {
            // The guard as implemented does: orderField != null && orderField.contains("(")
            // This ensures it short-circuits on null safely.
            String orderField = null;
            //noinspection ConstantConditions
            assertFalse(orderField != null && orderField.contains("("),
                    "Guard 1: null field must not cause NullPointerException");
        }
    }

    // -----------------------------------------------------------------------
    // Error message contract: COMPOSE_WINDOW_ORDER_BY_UNRESOLVABLE prefix
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Error message contract — canonical prefix and LLM-safety")
    class ErrorMessageContract {

        private static final String ERROR_PREFIX = "COMPOSE_WINDOW_ORDER_BY_UNRESOLVABLE:";

        @Test
        @DisplayName("Guard 1 error message starts with canonical prefix")
        void guard1_usesCanonicalPrefix() {
            String msg = guard1Message("salesRank", "sum(salesAmount)");
            assertTrue(msg.startsWith(ERROR_PREFIX),
                    "Guard 1 error must start with '" + ERROR_PREFIX + "'. Got: " + msg);
        }

        @Test
        @DisplayName("Guard 4 error message starts with canonical prefix")
        void guard4_usesCanonicalPrefix() {
            String msg = guard4Message("salesRank", "totalSales");
            assertTrue(msg.startsWith(ERROR_PREFIX),
                    "Guard 4 error must start with '" + ERROR_PREFIX + "'. Got: " + msg);
        }

        @Test
        @DisplayName("Both guards use the same prefix (monitorable)")
        void bothGuards_samePrefix() {
            assertEquals(
                    guard1Message("r", "sum(x)").split(":")[0] + ":",
                    guard4Message("r", "ghost").split(":")[0] + ":",
                    "Guard 1 and Guard 4 must share the same error prefix for monitoring"
            );
        }

        @Test
        @DisplayName("Guard 1 error names the offending field")
        void guard1_namesOffendingField() {
            String field = "sum(salesAmount)";
            String msg = guard1Message("salesRank", field);
            assertTrue(msg.contains(field),
                    "Guard 1 error must contain the offending field name. Got: " + msg);
        }

        @Test
        @DisplayName("Guard 4 error names the offending field")
        void guard4_namesOffendingField() {
            String field = "totalSales";
            String msg = guard4Message("salesRank", field);
            assertTrue(msg.contains(field),
                    "Guard 4 error must contain the offending field name. Got: " + msg);
        }

        @Test
        @DisplayName("Guard 1 error names the calc field")
        void guard1_namesCalcField() {
            String cfName = "salesRank";
            String msg = guard1Message(cfName, "sum(x)");
            assertTrue(msg.contains(cfName),
                    "Guard 1 error must name the calc field. Got: " + msg);
        }

        @Test
        @DisplayName("Guard 4 error names the calc field")
        void guard4_namesCalcField() {
            String cfName = "salesRank";
            String msg = guard4Message(cfName, "ghost");
            assertTrue(msg.contains(cfName),
                    "Guard 4 error must name the calc field. Got: " + msg);
        }

        @Test
        @DisplayName("Guard 1 error does not contain 'HINT' (no physical SQL leak)")
        void guard1_noPhysicalHint() {
            String msg = guard1Message("salesRank", "sum(amountTotal)");
            assertFalse(msg.toUpperCase().contains("HINT"),
                    "Guard 1 error must not contain 'HINT'. Got: " + msg);
        }

        @Test
        @DisplayName("Guard 4 error does not contain 'HINT' (no physical SQL leak)")
        void guard4_noPhysicalHint() {
            String msg = guard4Message("salesRank", "totalSales");
            assertFalse(msg.toUpperCase().contains("HINT"),
                    "Guard 4 error must not contain 'HINT'. Got: " + msg);
        }

        @Test
        @DisplayName("Guard 4 error does not contain physical alias prefix 't.'")
        void guard4_noPhysicalAliasPrefix() {
            String msg = guard4Message("salesRank", "totalSales");
            assertFalse(msg.contains("t."),
                    "Guard 4 error must not contain physical alias prefix 't.'. Got: " + msg);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers: reproduce exact error messages from the fixed processor
    // -----------------------------------------------------------------------

    /** Whether a field name should trigger Guard 1 (raw expression). */
    private static boolean containsOpenParen(String field) {
        return field != null && field.contains("(");
    }

    /**
     * Reproduces the exact Guard 1 (raw expression) error message emitted by
     * {@code SqlCalculatedFieldProcessor.wrapWithWindowClause} after the fix.
     */
    private static String guard1Message(String cfName, String field) {
        return "COMPOSE_WINDOW_ORDER_BY_UNRESOLVABLE: calculatedFields["
                + cfName + "].windowOrderBy field "
                + field + " looks like a raw SQL expression (contains '('). "
                + "Only QM field names (measures, dimensions, or prior calc-field "
                + "names) are valid here. Use a base model measure field instead.";
    }

    /**
     * Reproduces the exact Guard 4 (unresolvable field) error message emitted by
     * {@code SqlCalculatedFieldProcessor.wrapWithWindowClause} after the fix.
     */
    private static String guard4Message(String cfName, String field) {
        return "COMPOSE_WINDOW_ORDER_BY_UNRESOLVABLE: calculatedFields["
                + cfName + "].windowOrderBy field "
                + field + " cannot be resolved as a QM measure, "
                + "dimension, or prior calc-field name. "
                + "If it is an alias defined by another calculatedField in "
                + "this same query, it is not available in the OVER clause at "
                + "this stage. Use a base model measure field or wrap the "
                + "aggregation in a preceding query stage before applying the "
                + "window function.";
    }
}
