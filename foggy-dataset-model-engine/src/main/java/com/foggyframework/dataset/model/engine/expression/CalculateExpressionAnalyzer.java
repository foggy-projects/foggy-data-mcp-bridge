package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.dataset.model.engine.expression.sql.SqlBinaryExp;
import com.foggyframework.dataset.model.engine.expression.sql.SqlCalculateExp;
import com.foggyframework.dataset.model.engine.expression.sql.SqlFunctionExp;
import com.foggyframework.dataset.model.engine.expression.sql.SqlListExp;
import com.foggyframework.dataset.model.engine.expression.sql.SqlLiteralExp;
import com.foggyframework.dataset.model.engine.expression.sql.SqlRemoveExp;
import com.foggyframework.dataset.model.engine.expression.sql.SqlUnaryExp;
import com.foggyframework.fsscript.parser.spi.Exp;

/**
 * Structural validation for restricted CALCULATE expressions.
 */
public final class CalculateExpressionAnalyzer {

    private CalculateExpressionAnalyzer() {
    }

    public static void validate(Exp exp) {
        if (!containsCalculate(exp)) {
            return;
        }
        validateNoNestedCalculate(exp, 0);
        validateNoBareCalculateDivision(exp);
    }

    public static boolean containsCalculate(Exp exp) {
        Exp node = unwrap(exp);
        if (node == null) {
            return false;
        }
        if (node instanceof SqlCalculateExp) {
            return true;
        }
        if (node instanceof SqlBinaryExp binary) {
            return containsCalculate(binary.getLeft()) || containsCalculate(binary.getRight());
        }
        if (node instanceof SqlUnaryExp unary) {
            return containsCalculate(unary.getOperand());
        }
        if (node instanceof SqlFunctionExp function) {
            for (Exp arg : function.getArgs()) {
                if (containsCalculate(arg)) {
                    return true;
                }
            }
        }
        if (node instanceof SqlListExp list) {
            for (Exp item : list.getItems()) {
                if (containsCalculate(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void validateNoNestedCalculate(Exp exp, int depth) {
        Exp node = unwrap(exp);
        if (node == null) {
            return;
        }
        if (node instanceof SqlCalculateExp calculate) {
            if (depth > 0) {
                throw new IllegalArgumentException("CALCULATE_NESTED_UNSUPPORTED");
            }
            for (Exp arg : calculate.getArgs()) {
                validateNoNestedCalculate(arg, depth + 1);
            }
            return;
        }
        if (node instanceof SqlBinaryExp binary) {
            validateNoNestedCalculate(binary.getLeft(), depth);
            validateNoNestedCalculate(binary.getRight(), depth);
        } else if (node instanceof SqlUnaryExp unary) {
            validateNoNestedCalculate(unary.getOperand(), depth);
        } else if (node instanceof SqlFunctionExp function) {
            String name = function.getFunctionName();
            if (depth == 0 && isAggregateOrWindow(name)) {
                for (Exp arg : function.getArgs()) {
                    if (containsCalculate(arg)) {
                        throw new IllegalArgumentException("CALCULATE_EXPR_UNSUPPORTED");
                    }
                }
            }
            for (Exp arg : function.getArgs()) {
                validateNoNestedCalculate(arg, depth);
            }
        } else if (node instanceof SqlListExp list) {
            for (Exp item : list.getItems()) {
                validateNoNestedCalculate(item, depth);
            }
        } else if (node instanceof SqlRemoveExp remove) {
            for (Exp arg : remove.getArgs()) {
                validateNoNestedCalculate(arg, depth);
            }
        }
    }

    private static void validateNoBareCalculateDivision(Exp exp) {
        Exp node = unwrap(exp);
        if (node == null) {
            return;
        }
        if (node instanceof SqlBinaryExp binary) {
            if ("/".equals(binary.getOperator())
                    && containsCalculate(binary.getRight())
                    && !isNullifCalculate(binary.getRight())) {
                throw new IllegalArgumentException("CALCULATE_RATIO_REQUIRES_NULLIF");
            }
            validateNoBareCalculateDivision(binary.getLeft());
            validateNoBareCalculateDivision(binary.getRight());
        } else if (node instanceof SqlUnaryExp unary) {
            validateNoBareCalculateDivision(unary.getOperand());
        } else if (node instanceof SqlFunctionExp function) {
            for (Exp arg : function.getArgs()) {
                validateNoBareCalculateDivision(arg);
            }
        } else if (node instanceof SqlCalculateExp calculate) {
            for (Exp arg : calculate.getArgs()) {
                validateNoBareCalculateDivision(arg);
            }
        } else if (node instanceof SqlListExp list) {
            for (Exp item : list.getItems()) {
                validateNoBareCalculateDivision(item);
            }
        }
    }

    private static boolean isNullifCalculate(Exp exp) {
        Exp node = unwrap(exp);
        if (!(node instanceof SqlFunctionExp function)
                || !"NULLIF".equalsIgnoreCase(function.getFunctionName())
                || function.getArgs().size() != 2) {
            return false;
        }
        return unwrap(function.getArgs().get(0)) instanceof SqlCalculateExp
                && isZeroLiteral(function.getArgs().get(1));
    }

    private static boolean isZeroLiteral(Exp exp) {
        Exp node = unwrap(exp);
        if (!(node instanceof SqlLiteralExp literal)) {
            return false;
        }
        String value = literal.getLiteral();
        if (value == null) {
            return false;
        }
        return "0".equals(value.trim()) || "0.0".equals(value.trim());
    }

    private static boolean isAggregateOrWindow(String name) {
        return AllowedFunctions.isAggregateFunction(name) || AllowedFunctions.isWindowFunction(name);
    }

    private static Exp unwrap(Exp exp) {
        Exp current = exp;
        while (current instanceof SqlExpHolder) {
            Exp inner = ((SqlExpHolder) current).getInnerSqlExp();
            if (inner == null || inner == current) {
                break;
            }
            current = inner;
        }
        return current;
    }
}
