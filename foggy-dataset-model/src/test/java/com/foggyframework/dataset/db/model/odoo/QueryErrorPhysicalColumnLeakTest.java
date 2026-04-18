package com.foggyframework.dataset.db.model.odoo;

import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.util.QueryErrorSanitizer;
import com.foggyframework.dataset.db.model.spi.PhysicalColumnMapping;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for BUG-007-v1.3: physical column leak in engine error messages.
 *
 * <p>Background: when a query reaches the database with a column reference
 * that the database rejects (e.g. slipped through schema validation, or
 * produced by a future query shape), the executor surfaces a raw DB error
 * such as:</p>
 *
 * <pre>
 *   column t.move$date does not exist
 *   HINT:  Perhaps you meant to reference the column "t.move_name".
 * </pre>
 *
 * <p>The physical alias (<code>t</code>) and physical column
 * (<code>t.move_name</code>) must not escape the engine boundary.  Upstream
 * consumers (MCP, AI, end user) only know the QM surface and should not be
 * exposed to the underlying schema.</p>
 *
 * <p>The fix is {@link QueryErrorSanitizer}, wired into
 * {@code JdbcQueryModelImpl.queryJdbc()} so any executor exception is
 * wrapped in a {@code SanitizedQueryExecutionException} before it reaches
 * the MCP boundary.  This test class pins the two governance layers:</p>
 *
 * <ol>
 *   <li>Pre-SQL validator (Java {@code SchemaAwareFieldValidationStep}) —
 *       still catches the common form of the bug with a QM-level did-you-mean.</li>
 *   <li>Post-execution sanitizer ({@link QueryErrorSanitizer}) — strips
 *       physical alias / column names and rewrites DB HINT clauses into
 *       QM-level did-you-mean suggestions, using the model's
 *       {@link PhysicalColumnMapping} to translate physical columns back
 *       to their QM field names.</li>
 * </ol>
 *
 * <p>The user-reported case was {@code OdooAccountMoveLineQueryModel}, but
 * that model's backing table ({@code account_move_line}) is not part of
 * the SQLite integration schema.  We use {@code OdooAccountMoveQueryModel}
 * here — same governance boundary, same code path, same bug shape — and
 * the user-reported string is still exercised verbatim as an input to
 * {@link QueryErrorSanitizer}.</p>
 *
 * <p>Ref: docs/v1.3/BUG-007-engine-error-exposes-physical-column.md</p>
 */
@DisplayName("Query error must not expose physical column names (BUG-007-v1.3)")
class QueryErrorPhysicalColumnLeakTest extends EcommerceTestSupport {

    private static final String QM_NAME = "OdooAccountMoveQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryService;

    // -------------------------------------------------------------------
    // Part 1 — schema validator still catches the happy-path case.
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("schema validator catches invalid slice field with QM-level error")
    class SchemaAwareFieldValidationRecovery {

        @Test
        @DisplayName("slice.partner$date on OdooAccountMoveQueryModel returns INVALID_QUERY_FIELD")
        void partnerDollarDateInSliceReturnsInvalidQueryField() {
            // partner dim exposes email/phone/city/isCompany — 'date' is NOT a
            // partner property, so 'partner$date' must be rejected with a QM-level
            // did-you-mean before any SQL reaches the database.
            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setColumns(List.of("name", "amountTotal"));

            SemanticQueryRequest.SliceItem slice = new SemanticQueryRequest.SliceItem();
            slice.setField("partner$date");
            slice.setOp(">=");
            slice.setValue("2026-04-01");
            request.setSlice(List.of(slice));
            request.setLimit(10);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    semanticQueryService.queryModel(QM_NAME, request, "execute",
                            SemanticRequestContext.empty()));

            String msg = ex.getMessage();
            assertNotNull(msg);
            // Must use QM vocabulary — no physical name / HINT leak at this layer
            assertFalse(msg.contains("HINT:"),
                    "validator error leaked DB HINT clause: " + msg);
            assertFalse(msg.toLowerCase().contains("does not exist"),
                    "validator error used DB wording instead of QM wording: " + msg);
            assertTrue(msg.contains("partner$date"),
                    "validator error should identify the invalid QM field: " + msg);
            assertTrue(msg.contains("Did you mean"),
                    "validator should offer a QM-level suggestion: " + msg);

            assertInstanceOf(ExRuntimeExceptionImpl.class, ex);
            Object item = ((ExRuntimeExceptionImpl) ex).getItem();
            assertNotNull(item);
        }
    }

    // -------------------------------------------------------------------
    // Part 2 — post-execution sanitizer contract.
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("QueryErrorSanitizer strips physical identifiers and rewrites HINT")
    class SanitizerContract {

        private QueryModel qm() {
            QueryModel model = (QueryModel) queryModelLoader.getJdbcQueryModel(QM_NAME, null);
            assertNotNull(model, QM_NAME + " must be loaded in the test bundle");
            return model;
        }

        @Test
        @DisplayName("canonical PostgreSQL error — physical alias + HINT are both scrubbed")
        void postgresCanonicalError() {
            QueryModel model = qm();
            PhysicalColumnMapping mapping = model.getPhysicalColumnMapping();
            assertNotNull(mapping, QM_NAME + " should expose a PhysicalColumnMapping");

            // User-reported raw DB error: physical alias `t` plus a HINT
            // citing a physical column.  Use amount_untaxed (physical) which
            // maps to amountUntaxed (QM) so we can also verify translation.
            String raw = "column t.partner$date does not exist\n"
                    + "HINT:  Perhaps you meant to reference the column \"t.amount_untaxed\".";

            String sanitized = QueryErrorSanitizer.sanitize(raw, model);

            // Physical schema must not escape
            assertFalse(sanitized.contains("t.amount_untaxed"),
                    "sanitizer leaked <alias>.<phys_col> pattern: " + sanitized);
            assertFalse(sanitized.contains("amount_untaxed\""),
                    "sanitizer leaked quoted physical column: " + sanitized);
            assertFalse(sanitized.matches("(?s).*\\bt\\.\\w+.*"),
                    "sanitizer left a raw <alias>.<col> pattern: " + sanitized);
            assertFalse(sanitized.contains("HINT:"),
                    "sanitizer forwarded raw DB HINT clause: " + sanitized);

            // QM vocabulary is preserved / translated
            assertTrue(sanitized.contains("partner$date"),
                    "sanitizer should keep the offending QM token: " + sanitized);
            assertTrue(sanitized.contains("amountUntaxed"),
                    "sanitizer should translate physical 'amount_untaxed' to QM 'amountUntaxed': " + sanitized);
            assertTrue(sanitized.contains("Did you mean"),
                    "sanitizer should rewrite HINT to QM did-you-mean: " + sanitized);
            assertTrue(sanitized.contains(QM_NAME),
                    "sanitizer should prepend the model name for audit: " + sanitized);
        }

        @Test
        @DisplayName("user-reported raw error (move_name / account_move_line context) is scrubbed")
        void userReportedRawError() {
            // The user's original error text as filed in BUG-007.  Even when
            // the mapping for the specific model does not contain move_name,
            // the sanitizer must at minimum strip the alias prefix so nothing
            // like `t.move_name` survives.
            QueryModel model = qm();
            String raw = "column t.move$date does not exist\n"
                    + "HINT:  Perhaps you meant to reference the column \"t.move_name\".";

            String sanitized = QueryErrorSanitizer.sanitize(raw, model);

            assertFalse(sanitized.contains("t.move_name"),
                    "sanitizer leaked physical column: " + sanitized);
            assertFalse(sanitized.matches("(?s).*\\bt\\.\\w+.*"),
                    "sanitizer left a raw <alias>.<col> pattern: " + sanitized);
            assertFalse(sanitized.contains("HINT:"),
                    "sanitizer forwarded raw DB HINT clause: " + sanitized);
            assertTrue(sanitized.contains("move$date"),
                    "sanitizer should keep the offending QM token: " + sanitized);
        }

        @Test
        @DisplayName("MySQL-style error — alias in quoted identifier is stripped")
        void mysqlStyleUnknownColumn() {
            QueryModel model = qm();
            String raw = "Unknown column 't.partner$date' in 'where clause'";

            String sanitized = QueryErrorSanitizer.sanitize(raw, model);

            assertFalse(sanitized.matches("(?s).*\\bt\\.\\w+.*"),
                    "sanitizer left a raw <alias>.<col> pattern: " + sanitized);
            assertTrue(sanitized.contains("partner$date"),
                    "sanitizer should keep the offending QM token: " + sanitized);
            assertTrue(sanitized.contains(QM_NAME),
                    "sanitizer should prepend the model name for audit: " + sanitized);
        }

        @Test
        @DisplayName("SQLite-style error — no HINT, alias stripped")
        void sqliteStyleNoSuchColumn() {
            QueryModel model = qm();
            String raw = "no such column: t.partner$date";

            String sanitized = QueryErrorSanitizer.sanitize(raw, model);

            assertFalse(sanitized.matches("(?s).*\\bt\\.\\w+.*"),
                    "sanitizer left a raw <alias>.<col> pattern: " + sanitized);
            assertTrue(sanitized.contains("partner$date"),
                    "sanitizer should keep the offending QM token: " + sanitized);
        }

        @Test
        @DisplayName("sanitize() with null query model — still strips alias, no crash")
        void nullQueryModelStillSanitizes() {
            String raw = "column t.partner$date does not exist";
            String sanitized = QueryErrorSanitizer.sanitize(raw, (QueryModel) null);
            assertFalse(sanitized.contains("t.partner$date"),
                    "sanitizer should strip alias even without mapping: " + sanitized);
            assertTrue(sanitized.contains("partner$date"),
                    "sanitizer should keep the QM token: " + sanitized);
        }

        @Test
        @DisplayName("already-sanitized message is idempotent")
        void idempotent() {
            QueryModel model = qm();
            String raw = "column t.partner$date does not exist\n"
                    + "HINT:  Perhaps you meant to reference the column \"t.amount_untaxed\".";
            String once = QueryErrorSanitizer.sanitize(raw, model);
            String twice = QueryErrorSanitizer.sanitize(once, model);
            assertEquals(once, twice,
                    "running sanitize twice should be a no-op; got: " + twice);
        }

        @Test
        @DisplayName("null / empty input returns empty string without crash")
        void nullAndEmptyInputs() {
            QueryModel model = qm();
            assertEquals("", QueryErrorSanitizer.sanitize(null, model));
            assertEquals("", QueryErrorSanitizer.sanitize("", model));
        }
    }
}
