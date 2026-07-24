package com.foggyframework.dataset.model.engine.compose.plan;

/**
 * Internal bridge used by the fsscript runtime to bind script variable names
 * to query plans without adding another callable method to {@link QueryPlan}.
 */
public final class PlanAliasSupport {

    private PlanAliasSupport() {
    }

    public static void bindAlias(QueryPlan plan, String alias) {
        if (plan == null || alias == null || !isIdentifier(alias)) {
            return;
        }
        plan.addComposeSourceAlias(alias);
    }

    private static boolean isIdentifier(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        char first = text.charAt(0);
        if (!(first == '_' || first == '$' || Character.isLetter(first))) {
            return false;
        }
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c == '_' || c == '$' || Character.isLetterOrDigit(c))) {
                return false;
            }
        }
        return true;
    }
}
