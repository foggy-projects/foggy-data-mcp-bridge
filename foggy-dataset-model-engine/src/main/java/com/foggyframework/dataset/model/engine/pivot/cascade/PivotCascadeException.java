package com.foggyframework.dataset.model.engine.pivot.cascade;

/**
 * Fail-closed exception for cascade Generate semantics.
 *
 * <p>The message intentionally starts with the structured code so an LLM or
 * API caller can choose a safer rewrite instead of treating the result as a
 * generic runtime failure.</p>
 */
public class PivotCascadeException extends IllegalArgumentException {

    private final PivotCascadeErrorCode code;

    public PivotCascadeException(PivotCascadeErrorCode code, String guidance) {
        super(code.name() + ": " + guidance);
        this.code = code;
    }

    public PivotCascadeException(PivotCascadeErrorCode code, String guidance, Throwable cause) {
        super(code.name() + ": " + guidance, cause);
        this.code = code;
    }

    public PivotCascadeErrorCode getCode() {
        return code;
    }

    public static PivotCascadeException orderByRequired(String field, Integer limit) {
        return new PivotCascadeException(
                PivotCascadeErrorCode.PIVOT_CASCADE_ORDER_BY_REQUIRED,
                "Multi-level TopN requires explicit orderBy at every limited level. " +
                        "Field '" + field + "' has limit=" + limit + " but no orderBy. " +
                        "Please add orderBy, remove this limit, or reduce the request to single-level TopN.");
    }

    public static PivotCascadeException sqlRequired(String detail) {
        return new PivotCascadeException(
                PivotCascadeErrorCode.PIVOT_CASCADE_SQL_REQUIRED,
                "Multi-level TopN requires staged SQL execution. This request cannot safely fall back to memory execution. " +
                        "Please remove one axis limit, simplify to single-level TopN, or run on a dialect with CTE and window function support. " +
                        detail);
    }

    public static PivotCascadeException sqlRequired(String detail, Throwable cause) {
        return new PivotCascadeException(
                PivotCascadeErrorCode.PIVOT_CASCADE_SQL_REQUIRED,
                "Multi-level TopN requires staged SQL execution. This request cannot safely fall back to memory execution. " +
                        "Please remove one axis limit, simplify to single-level TopN, or run on a dialect with CTE and window function support. " +
                        detail,
                cause);
    }

    public static PivotCascadeException nonAdditiveRejected(String metric) {
        return new PivotCascadeException(
                PivotCascadeErrorCode.PIVOT_CASCADE_NON_ADDITIVE_REJECTED,
                "Multi-level TopN with non-additive metrics is rejected because correctness cannot be guaranteed for ranking, having, subtotal, or grandTotal. " +
                        "Metric '" + metric + "' participates in a cascade request. " +
                        "Please disable subtotal/grandTotal, use additive metrics only, or query leaf results without cascade.");
    }

    public static PivotCascadeException crossAxisRejected(String detail) {
        return new PivotCascadeException(
                PivotCascadeErrorCode.PIVOT_CASCADE_CROSS_AXIS_REJECTED,
                "C2 v1 does not support row and column ranking interactions. " +
                        "Please keep cascade TopN on rows only and remove column-axis TopN/having. " +
                        detail);
    }

    public static PivotCascadeException treeRejected() {
        return new PivotCascadeException(
                PivotCascadeErrorCode.PIVOT_CASCADE_TREE_REJECTED,
                "hierarchyMode=tree cannot be combined with cascade TopN in C2 v1. " +
                        "Please remove hierarchyMode=tree or remove the cascade limit/having levels.");
    }

    public static PivotCascadeException scopeUnsupported(String detail) {
        return new PivotCascadeException(
                PivotCascadeErrorCode.PIVOT_CASCADE_SCOPE_UNSUPPORTED,
                "This cascade Generate request is outside C2 v1 scope. " +
                        "Please simplify to a two-level rows cascade with additive metrics and explicit orderBy. " +
                        detail);
    }
}
