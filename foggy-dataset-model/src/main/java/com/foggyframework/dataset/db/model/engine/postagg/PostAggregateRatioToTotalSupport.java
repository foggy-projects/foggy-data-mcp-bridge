package com.foggyframework.dataset.db.model.engine.postagg;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.query.request.PostAggregateCalculationDef;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PostAggregateRatioToTotalSupport {

    private static final Pattern RATIO_TO_TOTAL_SUGAR_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:ratio_to_total|ratioToTotal)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)\\s*$");

    private PostAggregateRatioToTotalSupport() {
    }

    public static PostAggregateCalculationDef toCalculation(String name,
                                                            String expression,
                                                            Set<String> selectedAggregateAliases) {
        if (StringUtils.isEmpty(expression)) {
            return null;
        }
        Matcher sugar = RATIO_TO_TOTAL_SUGAR_PATTERN.matcher(expression);
        if (sugar.matches()) {
            return new PostAggregateCalculationDef(name, "ratioToTotal", sugar.group(1), "grandTotal", "ratio");
        }
        if (selectedAggregateAliases == null || selectedAggregateAliases.isEmpty()) {
            return null;
        }
        String normalized = stripOuterParentheses(expression.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""));
        String format = "ratio";
        if (normalized.endsWith("*100")) {
            normalized = stripOuterParentheses(normalized.substring(0, normalized.length() - 4));
            format = "percent";
        } else if (normalized.startsWith("100*")) {
            normalized = stripOuterParentheses(normalized.substring(4));
            format = "percent";
        }
        for (String alias : selectedAggregateAliases) {
            if (StringUtils.isEmpty(alias)) {
                continue;
            }
            String lowerAlias = alias.toLowerCase(Locale.ROOT);
            if (matchesRatioToGrandTotal(normalized, lowerAlias)) {
                return new PostAggregateCalculationDef(name, "ratioToTotal", alias, "grandTotal", format);
            }
        }
        return null;
    }

    private static boolean matchesRatioToGrandTotal(String normalized, String alias) {
        return (alias + "/sum(" + alias + ")over()").equals(normalized)
                || (alias + "/nullif(sum(" + alias + ")over(),0)").equals(normalized)
                || (alias + "/sum(" + alias + ")").equals(normalized)
                || (alias + "/nullif(sum(" + alias + "),0)").equals(normalized);
    }

    private static String stripOuterParentheses(String expression) {
        String current = expression == null ? "" : expression;
        while (current.length() >= 2 && current.charAt(0) == '(' && current.charAt(current.length() - 1) == ')'
                && wrapsWholeExpression(current)) {
            current = current.substring(1, current.length() - 1);
        }
        return current;
    }

    private static boolean wrapsWholeExpression(String expression) {
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char ch = expression.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0 && i < expression.length() - 1) {
                    return false;
                }
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0;
    }
}
