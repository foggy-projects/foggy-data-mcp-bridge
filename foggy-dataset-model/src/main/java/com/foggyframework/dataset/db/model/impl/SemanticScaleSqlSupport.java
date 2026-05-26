package com.foggyframework.dataset.db.model.impl;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class SemanticScaleSqlSupport {

    private static final Pattern SIMPLE_COLUMN_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private SemanticScaleSqlSupport() {
    }

    public static boolean hasScale(BigDecimal semanticScaleFactor) {
        return semanticScaleFactor != null;
    }

    public static void validate(BigDecimal semanticScaleFactor, String column, boolean hasFormula, String fieldName) {
        if (!hasScale(semanticScaleFactor)) {
            return;
        }
        RX.isTrue(semanticScaleFactor.compareTo(BigDecimal.ZERO) > 0,
                "semanticScaleFactor 必须大于 0: " + fieldName);
        if (hasFormula && StringUtils.isEmpty(column)) {
            return;
        }
        RX.isTrue(StringUtils.isNotEmpty(column) && SIMPLE_COLUMN_NAME.matcher(column).matches(),
                "semanticScaleFactor 字段的 column 必须是物理列名，不能是 SQL 表达式: " + fieldName);
    }

    public static String scaledDeclare(String baseDeclare, BigDecimal semanticScaleFactor) {
        if (!hasScale(semanticScaleFactor)) {
            return baseDeclare;
        }
        return "((" + baseDeclare + ") / " + sqlLiteral(semanticScaleFactor) + ")";
    }

    private static String sqlLiteral(BigDecimal semanticScaleFactor) {
        String literal = semanticScaleFactor.stripTrailingZeros().toPlainString();
        return literal.contains(".") ? literal : literal + ".0";
    }
}
