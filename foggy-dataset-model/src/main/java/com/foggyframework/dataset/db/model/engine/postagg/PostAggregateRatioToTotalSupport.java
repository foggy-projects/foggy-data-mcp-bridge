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
    private static final Pattern CUMULATIVE_RATIO_TO_TOTAL_SUGAR_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:cumulative_ratio_to_total|cumulativeRatioToTotal)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*(?:,\\s*desc\\s*)?\\)\\s*$");
    private static final Pattern CUMULATIVE_SUM_SUGAR_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:cumulative_sum|cumulativeSum)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*(?:,\\s*desc\\s*)?\\)\\s*$");
    private static final Pattern RANK_BY_MEASURE_SUGAR_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:rank_by|rankBy|rank_desc|rankDesc)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*(?:,\\s*desc\\s*)?\\)\\s*$");

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
        Matcher cumulativeRatioSugar = CUMULATIVE_RATIO_TO_TOTAL_SUGAR_PATTERN.matcher(expression);
        if (cumulativeRatioSugar.matches()) {
            return new PostAggregateCalculationDef(name, "cumulativeRatioToTotal", cumulativeRatioSugar.group(1), "grandTotal", "ratio");
        }
        Matcher cumulativeSumSugar = CUMULATIVE_SUM_SUGAR_PATTERN.matcher(expression);
        if (cumulativeSumSugar.matches()) {
            return new PostAggregateCalculationDef(name, "cumulativeSum", cumulativeSumSugar.group(1), "grandTotal", "value");
        }
        Matcher rankSugar = RANK_BY_MEASURE_SUGAR_PATTERN.matcher(expression);
        if (rankSugar.matches()) {
            return new PostAggregateCalculationDef(name, "rankByMeasure", rankSugar.group(1), "grandTotal", "value");
        }
        if (selectedAggregateAliases == null || selectedAggregateAliases.isEmpty()) {
            return null;
        }
        String normalized = stripOuterParentheses(expression.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace("\"", "")
                .replace("`", ""));
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
            if (matchesCumulativeRatioToGrandTotal(normalized, lowerAlias)) {
                return new PostAggregateCalculationDef(name, "cumulativeRatioToTotal", alias, "grandTotal", format);
            }
            if (matchesCumulativeSum(normalized, lowerAlias)) {
                return new PostAggregateCalculationDef(name, "cumulativeSum", alias, "grandTotal", "value");
            }
            if (matchesRankByMeasure(normalized, lowerAlias)) {
                return new PostAggregateCalculationDef(name, "rankByMeasure", alias, "grandTotal", "value");
            }
        }
        if ("rank()".equals(normalized)) {
            return new PostAggregateCalculationDef(name, "rankByMeasure",
                    selectedAggregateAliases.iterator().next(), "grandTotal", "value");
        }
        return null;
    }

    private static boolean matchesRatioToGrandTotal(String normalized, String alias) {
        return (alias + "/sum(" + alias + ")over()").equals(normalized)
                || (alias + "/nullif(sum(" + alias + ")over(),0)").equals(normalized)
                || (alias + "/sum(" + alias + ")").equals(normalized)
                || (alias + "/nullif(sum(" + alias + "),0)").equals(normalized)
                || matchesCalculateRemoveGrandTotal(normalized, alias);
    }

    private static boolean matchesCumulativeRatioToGrandTotal(String normalized, String alias) {
        String cumulativeSum = cumulativeSumExpression(alias);
        String cumulativeSumWithoutFrame = cumulativeSumExpressionWithoutFrame(alias);
        return (cumulativeSum + "/sum(" + alias + ")over()").equals(normalized)
                || (cumulativeSum + "/nullif(sum(" + alias + ")over(),0)").equals(normalized)
                || (cumulativeSumWithoutFrame + "/sum(" + alias + ")over()").equals(normalized)
                || (cumulativeSumWithoutFrame + "/nullif(sum(" + alias + ")over(),0)").equals(normalized);
    }

    private static boolean matchesCumulativeSum(String normalized, String alias) {
        return cumulativeSumExpression(alias).equals(normalized)
                || cumulativeSumExpressionWithoutFrame(alias).equals(normalized);
    }

    private static boolean matchesRankByMeasure(String normalized, String alias) {
        return ("rank()over(orderby" + alias + "desc)").equals(normalized)
                || ("dense_rank()over(orderby" + alias + "desc)").equals(normalized)
                || ("row_number()over(orderby" + alias + "desc)").equals(normalized);
    }

    private static String cumulativeSumExpression(String alias) {
        return "sum(" + alias + ")over(orderby" + alias + "descrowsbetweenunboundedprecedingandcurrentrow)";
    }

    private static String cumulativeSumExpressionWithoutFrame(String alias) {
        return "sum(" + alias + ")over(orderby" + alias + "desc)";
    }

    private static boolean matchesCalculateRemoveGrandTotal(String normalized, String alias) {
        String calculatePrefix = alias + "/calculate(sum(" + alias + "),remove(";
        String nullifCalculatePrefix = alias + "/nullif(calculate(sum(" + alias + "),remove(";
        return (normalized.startsWith(calculatePrefix) && normalized.endsWith("))"))
                || (normalized.startsWith(nullifCalculatePrefix) && normalized.endsWith(")),0)"));
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
