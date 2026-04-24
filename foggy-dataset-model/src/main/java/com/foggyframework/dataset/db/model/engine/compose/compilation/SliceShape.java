package com.foggyframework.dataset.db.model.engine.compose.compilation;

import java.util.Map;

/**
 * Canonicalised form of one plan-level {@code slice} entry.
 *
 * <p>Plan-level slices accept two shapes:
 * <ul>
 *   <li>{@code {"field": F, "op": OP, "value": V}} — preferred</li>
 *   <li>{@code {F: V}} — single-key shortcut; op defaults to {@code "="}</li>
 * </ul>
 *
 * <p>Both are consumed by {@link PerBaseCompiler} (translating to v1.3
 * {@link com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest.SliceItem})
 * and by {@link ComposePlanner} (rendering to a {@code WHERE <field> <op> ?}
 * fragment for derived plans). The two used to parse the shape independently —
 * now both go through {@link #parse(Object)}.
 *
 * @since 8.2.0.beta
 */
final class SliceShape {

    final String field;
    final String op;
    final Object value;

    private SliceShape(String field, String op, Object value) {
        this.field = field;
        this.op = op;
        this.value = value;
    }

    /** Parse one plan-level slice entry into its canonical three-field form.
     *
     *  @throws ComposeCompileException
     *          ({@link ComposeCompileErrorCodes#UNSUPPORTED_PLAN_SHAPE},
     *          phase={@code plan-lower}) when {@code raw} is not a
     *          {@link Map} or the single-key shortcut has != 1 keys.
     */
    @SuppressWarnings("unchecked")
    static SliceShape parse(Object raw) {
        if (!(raw instanceof Map)) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                    ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                    "Plan slice entries must be Map, got "
                            + (raw == null ? "null" : raw.getClass().getSimpleName()));
        }
        Map<String, Object> entry = (Map<String, Object>) raw;
        if (entry.containsKey("field")) {
            Object opObj = entry.get("op");
            return new SliceShape(
                    String.valueOf(entry.get("field")),
                    opObj == null ? "=" : String.valueOf(opObj),
                    entry.get("value"));
        }
        if (entry.size() != 1) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                    ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                    "Plan slice shortcut must have exactly 1 key, got " + entry.keySet());
        }
        Map.Entry<String, Object> e = entry.entrySet().iterator().next();
        return new SliceShape(e.getKey(), "=", e.getValue());
    }
}
