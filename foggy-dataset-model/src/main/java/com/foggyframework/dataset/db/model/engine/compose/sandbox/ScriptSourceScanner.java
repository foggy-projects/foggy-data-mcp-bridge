package com.foggyframework.dataset.db.model.engine.compose.sandbox;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Layer A — pre-execution source scanner for Compose Query scripts.
 *
 * <p>Scans the raw script source text for forbidden identifiers and patterns
 * <b>before</b> fsscript compilation. This catches eval/Function/fetch/require/
 * Date/Object.getPrototypeOf/__context__ etc. at the earliest possible moment,
 * so that dangerous scripts never reach the evaluator.</p>
 *
 * <p>Stateless, thread-safe utility class.</p>
 *
 * @since 8.2.0.beta
 */
public final class ScriptSourceScanner {

    private ScriptSourceScanner() { /* utility */ }

    // ---------------------------------------------------------------
    // Pattern lists — keep in sync with P0 sandbox spec §Layer A
    // ---------------------------------------------------------------

    /** A-01 / A-02: eval / Function constructor */
    private static final List<Pattern> EVAL_PATTERNS = List.of(
            // eval(...)
            Pattern.compile("\\beval\\s*\\("),
            // new Function(...)
            Pattern.compile("\\bnew\\s+Function\\s*\\("),
            // Function(...)  — called without new
            Pattern.compile("(?<!\\.)\\bFunction\\s*\\(")
    );

    /** A-03: async / await / setTimeout / setInterval / Promise */
    private static final List<Pattern> ASYNC_PATTERNS = List.of(
            Pattern.compile("\\bawait\\b"),
            Pattern.compile("\\basync\\b"),
            Pattern.compile("\\bsetTimeout\\s*\\("),
            Pattern.compile("\\bsetInterval\\s*\\("),
            Pattern.compile("\\bPromise\\b")
    );

    /** A-03 also: network primitives */
    private static final List<Pattern> NETWORK_PATTERNS = List.of(
            Pattern.compile("\\bfetch\\s*\\("),
            Pattern.compile("\\bXMLHttpRequest\\b"),
            Pattern.compile("\\bWebSocket\\b")
    );

    /** A-04: global / reflective access */
    private static final List<Pattern> GLOBAL_PATTERNS = List.of(
            Pattern.compile("\\bglobalThis\\b"),
            Pattern.compile("\\bwindow\\b"),
            Pattern.compile("\\bself\\b"),
            Pattern.compile("\\bReflect\\b"),
            Pattern.compile("\\.\\s*getClass\\s*\\("),
            Pattern.compile("\\bObject\\s*\\.\\s*getPrototypeOf\\s*\\("),
            Pattern.compile("\\bObject\\s*\\.\\s*keys\\s*\\("),
            Pattern.compile("\\bObject\\s*\\.\\s*defineProperty\\s*\\(")
    );

    /** A-05: time access */
    private static final List<Pattern> TIME_PATTERNS = List.of(
            Pattern.compile("\\bDate\\s*\\.\\s*now\\s*\\("),
            Pattern.compile("\\bnew\\s+Date\\s*\\("),
            Pattern.compile("\\bDate\\s*\\.\\s*parse\\s*\\(")
    );

    /** A-08: context access */
    private static final List<Pattern> CONTEXT_PATTERNS = List.of(
            Pattern.compile("__context__"),
            Pattern.compile("\\bComposeQueryContext\\b"),
            Pattern.compile("\\bprincipal\\b"),
            Pattern.compile("\\bauthorityResolver\\b")
    );

    /** A-09: module / IO */
    private static final List<Pattern> IO_PATTERNS = List.of(
            Pattern.compile("\\brequire\\s*\\("),
            Pattern.compile("\\bimport\\s"),
            Pattern.compile("\\bprocess\\b"),
            Pattern.compile("\\bfs\\s*\\."),
            Pattern.compile("\\bFile\\b")
    );

    /** A-06 / A-07: security parameter keywords that must not appear in DSL bodies */
    private static final List<Pattern> SECURITY_PARAM_PATTERNS = List.of(
            Pattern.compile("\\bauthorization\\b\\s*:"),
            Pattern.compile("\\buserId\\b\\s*:"),
            Pattern.compile("\\btenantId\\b\\s*:"),
            Pattern.compile("\\broles\\b\\s*:"),
            // namespace is too common; only match inside object-key context
            Pattern.compile("\\bdeniedColumns\\b\\s*:"),
            Pattern.compile("\\bsystemSlice\\b\\s*:"),
            Pattern.compile("\\bfieldAccess\\b\\s*:"),
            Pattern.compile("\\bpolicySnapshotId\\b\\s*:")
    );

    /** Layer B: blocked SQL function names detected at source level */
    private static final List<Pattern> BLOCKED_SQL_FN_PATTERNS = List.of(
            Pattern.compile("(?i)\\bRAW_SQL\\s*\\("),
            Pattern.compile("(?i)\\bEXEC\\s*\\("),
            Pattern.compile("(?i)\\bXP_CMDSHELL\\s*\\("),
            Pattern.compile("(?i)\\bDBMS_PIPE\\s*\\(")
    );

    /** Layer C: forbidden QueryPlan method names */
    private static final List<Pattern> FORBIDDEN_METHOD_PATTERNS = List.of(
            Pattern.compile("\\.\\s*raw\\s*\\("),
            Pattern.compile("\\.\\s*memoryFilter\\s*\\("),
            Pattern.compile("\\.\\s*forEach\\s*\\("),
            Pattern.compile("\\.\\s*toArray\\s*\\(")
    );

    /** Layer C: result iteration patterns */
    private static final List<Pattern> RESULT_ITERATION_PATTERNS = List.of(
            Pattern.compile("\\.\\s*items\\s*\\."),
            Pattern.compile("\\.\\s*rows\\s*\\."),
            Pattern.compile("\\.\\s*iterator\\s*\\(")
    );

    // ---------------------------------------------------------------
    // Public scan entry point
    // ---------------------------------------------------------------

    /**
     * Scan the source for Layer A violations.
     *
     * @param script raw script source
     * @throws ComposeSandboxViolationException on any detected violation
     */
    public static void scan(String script) {
        if (script == null || script.isEmpty()) {
            return;
        }

        // A-01 / A-02: eval / Function
        if (matchesAny(script, EVAL_PATTERNS)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_A_EVAL_DENIED,
                    "Dynamic evaluation is not allowed in compose scripts.",
                    ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE);
        }

        // A-03: async primitives
        if (matchesAny(script, ASYNC_PATTERNS)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_A_ASYNC_DENIED,
                    "Asynchronous primitives are not allowed in compose scripts.",
                    ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE);
        }

        // A-03 also: network
        if (matchesAny(script, NETWORK_PATTERNS)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_A_NETWORK_DENIED,
                    "Network primitives are not available in compose scripts.",
                    ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE);
        }

        // A-04: global / reflective access
        if (matchesAny(script, GLOBAL_PATTERNS)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_A_GLOBAL_DENIED,
                    "Reflective or global access is blocked.",
                    ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE);
        }

        // A-05: time
        if (matchesAny(script, TIME_PATTERNS)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_A_TIME_DENIED,
                    "Direct time access is blocked; time must be injected by host.",
                    ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE);
        }

        // A-08: context access
        if (matchesAny(script, CONTEXT_PATTERNS)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_A_CONTEXT_ACCESS,
                    "ComposeQueryContext is not accessible from scripts.",
                    ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE);
        }

        // A-06 / A-07: security parameters in DSL body
        if (matchesAny(script, SECURITY_PARAM_PATTERNS)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_A_SECURITY_PARAM,
                    "Security parameters cannot be passed through DSL body; "
                            + "they are bound by ComposeQueryContext.",
                    ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE);
        }

        // Layer B: blocked SQL functions at source level (e.g. RAW_SQL)
        if (matchesAny(script, BLOCKED_SQL_FN_PATTERNS)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_B_DERIVED_FN_DENIED,
                    "Function is not allowed in compose scripts.",
                    ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE);
        }

        // Layer C: result iteration (check BEFORE forbidden methods
        // since patterns like .items.forEach(...) should match iteration first)
        if (matchesAny(script, RESULT_ITERATION_PATTERNS)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_C_RESULT_ITERATION,
                    "DataSetResult does not support script-side iteration.",
                    ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE);
        }

        // Layer C: forbidden QueryPlan methods
        if (matchesAny(script, FORBIDDEN_METHOD_PATTERNS)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_C_METHOD_DENIED,
                    "Method is not part of the QueryPlan public surface.",
                    ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE);
        }

        // A-09: module / IO
        if (matchesAny(script, IO_PATTERNS)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_A_IO_DENIED,
                    "File/process/module primitives are not available in compose scripts.",
                    ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE);
        }
    }

    private static boolean matchesAny(String source, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(source).find()) {
                return true;
            }
        }
        return false;
    }
}
