package com.foggyframework.dataset.db.model.proxy;

import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;
import jakarta.persistence.criteria.JoinType;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JOIN 构建器
 *
 * <p>用于构建表之间的 JOIN 关系，支持链式调用语法：
 * <pre>{@code
 * fo.leftJoin(fp).on(fo.orderId, fp.orderId)
 * fo.leftJoin(fp).on(fo.orderId, fp.orderId).and(fo.customerId, fp.customerId)
 * fo.innerJoin(fp).on(fo.orderId, fp.orderId).eq(fp.status, 'ACTIVE')
 * }</pre>
 *
 * @author Foggy Framework
 * @since 2.0
 */
@Getter
public class JoinBuilder implements PropertyFunction {

    /**
     * 左表（主表）代理
     */
    private final TableModelProxy left;

    /**
     * 右表（被 JOIN 的表）代理
     */
    private final TableModelProxy right;

    /**
     * JOIN 类型
     */
    private final JoinType joinType;

    /**
     * JOIN 条件列表
     */
    private final List<JoinCondition> conditions = new ArrayList<>();

    /**
     * 创建 JOIN 构建器
     *
     * @param left     左表代理
     * @param right    右表代理
     * @param joinType JOIN 类型
     */
    public JoinBuilder(TableModelProxy left, TableModelProxy right, JoinType joinType) {
        this.left = left;
        this.right = right;
        this.joinType = joinType;
    }

    /**
     * PropertyFunction 实现：支持链式方法调用
     *
     * <p>支持的方法：
     * <ul>
     *   <li>{@code on(leftCol, rightCol)} - 添加 ON 条件（列=列）</li>
     *   <li>{@code and(leftCol, rightCol)} - 添加 AND 条件（列=列）</li>
     *   <li>{@code eq(leftCol, value)} - 添加等值条件（列=常量）</li>
     *   <li>{@code neq(leftCol, value)} - 添加不等条件（列<>常量）</li>
     * </ul>
     */
    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        return switch (methodName) {
            case "on" -> {
                // on(fo.orderId, fp.orderId) - 主要的 ON 条件
                addCondition(toColumnRef(args[0]), "=", args[1]);
                yield this;
            }
            case "and" -> {
                // and(fo.customerId, fp.customerId) - 额外的 AND 条件
                addCondition(toColumnRef(args[0]), "=", args[1]);
                yield this;
            }
            case "eq" -> {
                // eq(fp.status, 'ACTIVE') - 等值常量条件
                addCondition(toColumnRef(args[0]), "=", args[1]);
                yield this;
            }
            case "neq" -> {
                // neq(fp.status, 'DELETED') - 不等常量条件
                addCondition(toColumnRef(args[0]), "<>", args[1]);
                yield this;
            }
            case "gt" -> {
                // gt(fo.amount, 0) - 大于条件
                addCondition(toColumnRef(args[0]), ">", args[1]);
                yield this;
            }
            case "gte" -> {
                // gte(fo.amount, 0) - 大于等于条件
                addCondition(toColumnRef(args[0]), ">=", args[1]);
                yield this;
            }
            case "lt" -> {
                // lt(fo.amount, 1000) - 小于条件
                addCondition(toColumnRef(args[0]), "<", args[1]);
                yield this;
            }
            case "lte" -> {
                // lte(fo.amount, 1000) - 小于等于条件
                addCondition(toColumnRef(args[0]), "<=", args[1]);
                yield this;
            }
            default -> PropertyHolder.NO_MATCH;
        };
    }

    /**
     * 将参数转换为 ColumnRef
     * <p>支持 ColumnRef 和 DimensionProxy 两种类型
     *
     * @param arg 参数对象
     * @return ColumnRef
     */
    private ColumnRef toColumnRef(Object arg) {
        if (arg instanceof ColumnRef columnRef) {
            return columnRef;
        }
        if (arg instanceof DimensionProxy dimensionProxy) {
            return dimensionProxy.toColumnRef();
        }
        throw new IllegalArgumentException("Expected ColumnRef or DimensionProxy, got: " + arg.getClass().getName());
    }

    /**
     * 添加 JOIN 条件
     *
     * @param left     左侧字段
     * @param operator 操作符
     * @param right    右侧值（ColumnRef 或常量）
     */
    private void addCondition(ColumnRef left, String operator, Object right) {
        conditions.add(new JoinCondition(left, operator, right));
    }

    /**
     * 获取不可变的条件列表
     *
     * @return 条件列表
     */
    public List<JoinCondition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    /**
     * 生成完整的 ON 子句 SQL
     *
     * <p>示例输出：{@code fo.order_id = fp.order_id AND fo.customer_id = fp.customer_id}
     *
     * @return ON 子句 SQL
     */
    public String buildOnClause() {
        if (conditions.isEmpty()) {
            return "";
        }
        return conditions.stream()
                .map(JoinCondition::toSqlFragment)
                .collect(Collectors.joining(" AND "));
    }

    /**
     * 获取所有常量参数值（用于 PreparedStatement）
     *
     * @return 常量参数列表
     */
    public List<Object> getConstantParameters() {
        return conditions.stream()
                .filter(c -> !c.isRightColumnRef())
                .map(JoinCondition::getConstantValue)
                .collect(Collectors.toList());
    }

    /**
     * 判断是否有条件
     *
     * @return 如果有条件返回 true
     */
    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    /**
     * 获取 JOIN 类型的 SQL 关键字
     *
     * @return JOIN 关键字（如 LEFT JOIN, INNER JOIN）
     */
    public String getJoinKeyword() {
        return switch (joinType) {
            case LEFT -> "LEFT JOIN";
            case RIGHT -> "RIGHT JOIN";
            case INNER -> "INNER JOIN";
        };
    }

    @Override
    public String toString() {
        return String.format("%s %s ON %s",
                getJoinKeyword(),
                right.getModelName(),
                buildOnClause());
    }
}
