package com.foggyframework.dataset.jdbc.model.engine.expression;

import com.foggyframework.dataset.jdbc.model.spi.DbColumnType;
import com.foggyframework.dataset.jdbc.model.spi.DbQueryColumn;
import lombok.Data;
import org.bson.Document;

import java.util.*;

/**
 * MongoDB 表达式片段
 * <p>
 * 封装生成的 MongoDB 聚合表达式及其引用的列信息。
 * 与 SqlFragment 类似，但存储的是 MongoDB Document 而非 SQL 字符串。
 * </p>
 *
 * <h3>MongoDB 表达式格式示例</h3>
 * <ul>
 *     <li>列引用: "$fieldName"</li>
 *     <li>加法: { $add: ["$a", "$b"] }</li>
 *     <li>减法: { $subtract: ["$a", "$b"] }</li>
 *     <li>YEAR 函数: { $year: "$dateField" }</li>
 * </ul>
 *
 * @author Foggy
 * @since 1.0
 */
@Data
public class MongoFragment {

    /**
     * MongoDB 表达式
     * <p>
     * 可以是：
     * <ul>
     *     <li>String - 列引用如 "$fieldName" 或字面量</li>
     *     <li>Document - 复杂表达式如 { $add: [...] }</li>
     *     <li>Number/Boolean - 字面量值</li>
     * </ul>
     */
    private Object expression;

    /**
     * 引用的列（包括普通列、计算字段）
     */
    private Set<DbQueryColumn> referencedColumns = new LinkedHashSet<>();

    /**
     * 推断的列类型
     */
    private DbColumnType inferredType = DbColumnType.UNKNOWN;

    /**
     * 是否包含聚合函数
     */
    private boolean hasAggregate = false;

    /**
     * 聚合函数类型（如果是单一顶层聚合）
     */
    private String aggregationType;

    /**
     * 创建列引用片段
     *
     * @param column    列对象
     * @param fieldName MongoDB 字段名
     */
    public static MongoFragment ofColumn(DbQueryColumn column, String fieldName) {
        MongoFragment f = new MongoFragment();
        f.expression = "$" + fieldName;
        f.referencedColumns.add(column);
        f.inferredType = inferColumnType(column);
        return f;
    }

    /**
     * 创建字面量片段
     */
    public static MongoFragment ofLiteral(Object value) {
        MongoFragment f = new MongoFragment();
        f.expression = value;
        f.inferredType = inferLiteralType(value);
        return f;
    }

    /**
     * 创建带类型的字面量片段
     */
    public static MongoFragment ofLiteral(Object value, DbColumnType type) {
        MongoFragment f = new MongoFragment();
        f.expression = value;
        f.inferredType = type;
        return f;
    }

    /**
     * 创建二元运算片段
     *
     * @param left     左操作数
     * @param operator MongoDB 运算符 ($add, $subtract, $multiply, $divide, $mod)
     * @param right    右操作数
     */
    public static MongoFragment binary(MongoFragment left, String operator, MongoFragment right) {
        MongoFragment f = new MongoFragment();
        f.expression = new Document(operator, Arrays.asList(left.expression, right.expression));
        f.referencedColumns.addAll(left.referencedColumns);
        f.referencedColumns.addAll(right.referencedColumns);
        f.inferredType = inferBinaryType(left.inferredType, operator, right.inferredType);
        f.hasAggregate = left.hasAggregate || right.hasAggregate;
        return f;
    }

    /**
     * 创建比较运算片段
     *
     * @param left     左操作数
     * @param operator MongoDB 比较运算符 ($eq, $ne, $gt, $gte, $lt, $lte)
     * @param right    右操作数
     */
    public static MongoFragment comparison(MongoFragment left, String operator, MongoFragment right) {
        MongoFragment f = new MongoFragment();
        f.expression = new Document(operator, Arrays.asList(left.expression, right.expression));
        f.referencedColumns.addAll(left.referencedColumns);
        f.referencedColumns.addAll(right.referencedColumns);
        f.inferredType = DbColumnType.BOOL;
        f.hasAggregate = left.hasAggregate || right.hasAggregate;
        return f;
    }

    /**
     * 创建逻辑运算片段
     *
     * @param operator MongoDB 逻辑运算符 ($and, $or)
     * @param operands 操作数列表
     */
    public static MongoFragment logical(String operator, List<MongoFragment> operands) {
        MongoFragment f = new MongoFragment();
        List<Object> exprs = new ArrayList<>();
        for (MongoFragment op : operands) {
            exprs.add(op.expression);
            f.referencedColumns.addAll(op.referencedColumns);
            if (op.hasAggregate) f.hasAggregate = true;
        }
        f.expression = new Document(operator, exprs);
        f.inferredType = DbColumnType.BOOL;
        return f;
    }

    /**
     * 创建一元运算片段
     *
     * @param operator MongoDB 运算符 ($not, $abs 等)
     * @param operand  操作数
     */
    public static MongoFragment unary(String operator, MongoFragment operand) {
        MongoFragment f = new MongoFragment();
        f.expression = new Document(operator, operand.expression);
        f.referencedColumns.addAll(operand.referencedColumns);
        f.inferredType = "$not".equals(operator) ? DbColumnType.BOOL : operand.inferredType;
        f.hasAggregate = operand.hasAggregate;
        f.aggregationType = operand.aggregationType;
        return f;
    }

    /**
     * 创建函数调用片段
     *
     * @param funcName MongoDB 函数运算符 ($year, $month, $concat 等)
     * @param args     参数列表
     */
    public static MongoFragment function(String funcName, List<MongoFragment> args) {
        MongoFragment f = new MongoFragment();

        // 根据参数个数决定表达式结构
        if (args.size() == 1) {
            f.expression = new Document(funcName, args.get(0).expression);
        } else {
            List<Object> exprs = new ArrayList<>();
            for (MongoFragment arg : args) {
                exprs.add(arg.expression);
            }
            f.expression = new Document(funcName, exprs);
        }

        // 收集引用的列
        args.forEach(arg -> f.referencedColumns.addAll(arg.referencedColumns));

        // 推断函数返回类型
        f.inferredType = inferFunctionType(funcName, args);

        // 检测聚合函数
        if (MongoAllowedFunctions.isAggregateFunction(funcName)) {
            f.hasAggregate = true;
            f.aggregationType = funcName.toUpperCase().replace("$", "");
        } else {
            f.hasAggregate = args.stream().anyMatch(MongoFragment::isHasAggregate);
        }

        return f;
    }

    /**
     * 创建条件表达式 ($cond)
     *
     * @param condition 条件
     * @param ifTrue    条件为真时的值
     * @param ifFalse   条件为假时的值
     */
    public static MongoFragment cond(MongoFragment condition, MongoFragment ifTrue, MongoFragment ifFalse) {
        MongoFragment f = new MongoFragment();
        f.expression = new Document("$cond", Arrays.asList(
                condition.expression,
                ifTrue.expression,
                ifFalse.expression
        ));
        f.referencedColumns.addAll(condition.referencedColumns);
        f.referencedColumns.addAll(ifTrue.referencedColumns);
        f.referencedColumns.addAll(ifFalse.referencedColumns);
        f.inferredType = ifTrue.inferredType;
        f.hasAggregate = condition.hasAggregate || ifTrue.hasAggregate || ifFalse.hasAggregate;
        return f;
    }

    /**
     * 创建空值处理表达式 ($ifNull)
     *
     * @param expr         表达式
     * @param defaultValue 默认值
     */
    public static MongoFragment ifNull(MongoFragment expr, MongoFragment defaultValue) {
        MongoFragment f = new MongoFragment();
        f.expression = new Document("$ifNull", Arrays.asList(expr.expression, defaultValue.expression));
        f.referencedColumns.addAll(expr.referencedColumns);
        f.referencedColumns.addAll(defaultValue.referencedColumns);
        f.inferredType = expr.inferredType;
        f.hasAggregate = expr.hasAggregate || defaultValue.hasAggregate;
        return f;
    }

    /**
     * 合并另一个片段的引用
     */
    public void mergeReferences(MongoFragment other) {
        if (other != null && other.referencedColumns != null) {
            this.referencedColumns.addAll(other.referencedColumns);
        }
    }

    /**
     * 获取表达式的 Document 形式（用于 $addFields）
     */
    public Document asAddFieldsEntry(String fieldName) {
        return new Document(fieldName, this.expression);
    }

    // ==========================================
    // 类型推断辅助方法
    // ==========================================

    private static DbColumnType inferLiteralType(Object value) {
        if (value == null) {
            return DbColumnType.UNKNOWN;
        }
        if (value instanceof String) {
            return DbColumnType.TEXT;
        }
        if (value instanceof Boolean) {
            return DbColumnType.BOOL;
        }
        if (value instanceof Double || value instanceof Float) {
            return DbColumnType.NUMBER;
        }
        if (value instanceof Number) {
            return DbColumnType.INTEGER;
        }
        if (value instanceof Date) {
            return DbColumnType.DATETIME;
        }
        return DbColumnType.UNKNOWN;
    }

    private static DbColumnType inferColumnType(DbQueryColumn column) {
        if (column == null) {
            return DbColumnType.UNKNOWN;
        }
        DbColumnType type = column.getSelectColumn() != null ? column.getSelectColumn().getType() : null;
        return type != null ? type : DbColumnType.UNKNOWN;
    }

    private static DbColumnType inferBinaryType(DbColumnType left, String operator, DbColumnType right) {
        // 比较运算符返回布尔
        if (operator.startsWith("$eq") || operator.startsWith("$ne") ||
            operator.startsWith("$gt") || operator.startsWith("$lt") ||
            operator.equals("$gte") || operator.equals("$lte")) {
            return DbColumnType.BOOL;
        }
        // 逻辑运算符返回布尔
        if ("$and".equals(operator) || "$or".equals(operator)) {
            return DbColumnType.BOOL;
        }
        // 算术运算符
        if ("$add".equals(operator) || "$subtract".equals(operator) ||
            "$multiply".equals(operator) || "$divide".equals(operator) || "$mod".equals(operator)) {
            if (left == DbColumnType.NUMBER || right == DbColumnType.NUMBER ||
                left == DbColumnType.MONEY || right == DbColumnType.MONEY) {
                return DbColumnType.NUMBER;
            }
            if (left == DbColumnType.INTEGER && right == DbColumnType.INTEGER) {
                return "$divide".equals(operator) ? DbColumnType.NUMBER : DbColumnType.INTEGER;
            }
            return DbColumnType.NUMBER;
        }
        // 字符串连接
        if ("$concat".equals(operator)) {
            return DbColumnType.TEXT;
        }
        return DbColumnType.UNKNOWN;
    }

    private static DbColumnType inferFunctionType(String funcName, List<MongoFragment> args) {
        // 日期提取函数 -> INTEGER
        if ("$year".equals(funcName) || "$month".equals(funcName) || "$dayOfMonth".equals(funcName) ||
            "$hour".equals(funcName) || "$minute".equals(funcName) || "$second".equals(funcName)) {
            return DbColumnType.INTEGER;
        }
        // 字符串函数 -> TEXT
        if ("$concat".equals(funcName) || "$substr".equals(funcName) || "$substrCP".equals(funcName) ||
            "$toLower".equals(funcName) || "$toUpper".equals(funcName) || "$trim".equals(funcName)) {
            return DbColumnType.TEXT;
        }
        // 字符串长度 -> INTEGER
        if ("$strLenCP".equals(funcName) || "$strLenBytes".equals(funcName)) {
            return DbColumnType.INTEGER;
        }
        // 数学函数 -> NUMBER
        if ("$abs".equals(funcName) || "$ceil".equals(funcName) || "$floor".equals(funcName) ||
            "$round".equals(funcName) || "$sqrt".equals(funcName) || "$pow".equals(funcName)) {
            return DbColumnType.NUMBER;
        }
        // 聚合函数
        if ("$sum".equals(funcName) || "$avg".equals(funcName)) {
            return DbColumnType.NUMBER;
        }
        if ("$count".equals(funcName)) {
            return DbColumnType.INTEGER;
        }
        if ("$min".equals(funcName) || "$max".equals(funcName)) {
            return args.isEmpty() ? DbColumnType.UNKNOWN : args.get(0).inferredType;
        }
        // 空值处理 -> 继承第一个参数类型
        if ("$ifNull".equals(funcName)) {
            return args.isEmpty() ? DbColumnType.UNKNOWN : args.get(0).inferredType;
        }
        return DbColumnType.UNKNOWN;
    }

    @Override
    public String toString() {
        return expression != null ? expression.toString() : "null";
    }
}
