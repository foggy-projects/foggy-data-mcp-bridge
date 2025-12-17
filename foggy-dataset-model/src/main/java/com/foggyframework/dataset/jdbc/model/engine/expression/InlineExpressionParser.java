package com.foggyframework.dataset.jdbc.model.engine.expression;

import com.foggyframework.core.utils.StringUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内联表达式解析器
 * <p>
 * 解析 columns 中的内联表达式，将其转换为 CalculatedFieldDef。
 * </p>
 *
 * <h3>支持的格式</h3>
 * <ul>
 *     <li>{@code YEAR(orderdate) AS orderYear} - 函数调用带别名</li>
 *     <li>{@code totaldue - discountAmount AS netAmount} - 算术表达式带别名</li>
 *     <li>{@code totaldue - discountAmount} - 算术表达式无别名（自动生成）</li>
 *     <li>{@code CONCAT(firstName, ' ', lastName)} - 多参数函数</li>
 * </ul>
 *
 * <h3>不处理的格式（返回 null）</h3>
 * <ul>
 *     <li>{@code columnName} - 简单列名</li>
 *     <li>{@code dimension$caption} - 维度引用</li>
 *     <li>{@code columnName AS alias} - 简单别名（无表达式）</li>
 * </ul>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public final class InlineExpressionParser {

    private InlineExpressionParser() {
        // 工具类，禁止实例化
    }

    /**
     * AS 关键字模式（不区分大小写）
     * 匹配: "expression AS alias" 或 "expression as alias"
     */
    private static final Pattern AS_PATTERN = Pattern.compile(
            "^(.+?)\\s+[Aa][Ss]\\s+(\\w+)\\s*$"
    );

    /**
     * 函数调用模式
     * 匹配: FUNC(...) 或 func(...)
     */
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
            "\\b[A-Za-z_][A-Za-z0-9_]*\\s*\\("
    );

    /**
     * 算术运算符模式
     * 匹配: +, -, *, /, %（排除维度引用中的 $）
     */
    private static final Pattern OPERATOR_PATTERN = Pattern.compile(
            "[+\\-*/%]"
    );

    /**
     * 简单列名模式
     * 匹配: columnName 或 table.columnName 或 dimension$caption
     */
    private static final Pattern SIMPLE_COLUMN_PATTERN = Pattern.compile(
            "^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?(\\$[A-Za-z_][A-Za-z0-9_]*)?$"
    );

    /**
     * 解析列定义
     * <p>
     * 判断是否为内联表达式，如果是则返回解析结果，否则返回 null。
     * </p>
     *
     * @param columnDef 列定义字符串
     * @return 解析结果，如果不是表达式返回 null
     */
    public static InlineExpression parse(String columnDef) {
        if (StringUtils.isEmpty(columnDef)) {
            return null;
        }

        String trimmed = columnDef.trim();
        String alias = null;
        String expression = trimmed;

        // 1. 检查是否有 AS 别名
        Matcher asMatcher = AS_PATTERN.matcher(trimmed);
        if (asMatcher.matches()) {
            expression = asMatcher.group(1).trim();
            alias = asMatcher.group(2).trim();

            // 如果表达式部分是简单列名，这只是普通的别名定义，不是内联表达式
            if (isSimpleColumnName(expression)) {
                return null;
            }
        }

        // 2. 判断是否为表达式
        if (isExpression(expression)) {
            if (log.isDebugEnabled()) {
                log.debug("Detected inline expression: '{}', alias: '{}'", expression, alias);
            }
            return new InlineExpression(expression, alias);
        }

        return null;
    }

    /**
     * 判断是否为简单列名
     */
    private static boolean isSimpleColumnName(String str) {
        return SIMPLE_COLUMN_PATTERN.matcher(str).matches();
    }

    /**
     * 判断是否为表达式（而非简单列名）
     */
    private static boolean isExpression(String str) {
        // 已经是简单列名
        if (isSimpleColumnName(str)) {
            return false;
        }

        // 包含函数调用
        if (FUNCTION_PATTERN.matcher(str).find()) {
            return true;
        }

        // 包含算术运算符（需要排除负数字面量如 -1）
        if (OPERATOR_PATTERN.matcher(str).find()) {
            // 进一步检查：确保不是单独的负数
            String withoutSpaces = str.replaceAll("\\s+", "");
            if (withoutSpaces.matches("^-?\\d+(\\.\\d+)?$")) {
                // 这只是一个数字
                return false;
            }
            return true;
        }

        // 包含括号（可能是复杂表达式）
        if (str.contains("(") && str.contains(")")) {
            return true;
        }

        return false;
    }

    /**
     * 内联表达式解析结果
     */
    @Data
    public static class InlineExpression {
        /**
         * 表达式内容
         */
        private final String expression;

        /**
         * 别名（可为 null，需要自动生成）
         */
        private final String alias;

        /**
         * 是否有显式别名
         */
        public boolean hasAlias() {
            return StringUtils.isNotEmpty(alias);
        }
    }
}
