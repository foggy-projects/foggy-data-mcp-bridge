package com.foggyframework.dataset.db.model.proxy;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JOIN 条件对象
 *
 * <p>表示 JOIN 语句中的一个条件，如 {@code fo.order_id = fp.order_id}
 *
 * <p>支持的条件类型：
 * <ul>
 *   <li>列与列比较：{@code on(fo.orderId, fp.orderId)}</li>
 *   <li>列与常量比较：{@code eq(fp.status, 'ACTIVE')}</li>
 * </ul>
 *
 * @author Foggy Framework
 * @since 2.0
 */
@Getter
public class JoinCondition {

    /**
     * 左侧字段引用
     */
    private final ColumnRef left;

    /**
     * 操作符（=, <>, <, >, <=, >=）
     */
    private final String operator;

    /**
     * 右侧值（可以是 ColumnRef 或常量值）
     */
    private final Object right;

    /**
     * 创建 JOIN 条件
     *
     * @param left     左侧字段引用
     * @param operator 操作符
     * @param right    右侧值（ColumnRef 或常量）
     */
    public JoinCondition(ColumnRef left, String operator, Object right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    /**
     * 判断右侧是否为字段引用
     *
     * @return 如果右侧是 ColumnRef 或 DimensionProxy 返回 true
     */
    public boolean isRightColumnRef() {
        return right instanceof ColumnRef || right instanceof DimensionProxy;
    }

    /**
     * 获取右侧的字段引用（如果是）
     *
     * @return 右侧字段引用，如果不是则返回 null
     */
    public ColumnRef getRightAsColumnRef() {
        if (right instanceof ColumnRef columnRef) {
            return columnRef;
        }
        if (right instanceof DimensionProxy dimensionProxy) {
            return dimensionProxy.toColumnRef();
        }
        return null;
    }

    /**
     * 生成 SQL ON 子句片段
     *
     * <p>示例输出：
     * <ul>
     *   <li>{@code fo.order_id = fp.order_id}</li>
     *   <li>{@code fp.status = ?}</li>
     * </ul>
     *
     * @return SQL 片段
     */
    public String toSqlFragment() {
        return toSqlFragment(null);
    }

    /**
     * 生成 SQL ON 子句片段。
     *
     * <p>如果提供 QueryModel，则优先将语义字段引用解析为物理列名和运行时别名；
     * 否则退回到 ColumnRef 自身携带的 tableAlias/columnName。
     *
     * @param queryModel 查询模型，可为空
     * @return SQL 片段
     */
    public String toSqlFragment(QueryModel queryModel) {
        String leftPart = buildColumnSql(left, queryModel);
        String rightPart;

        if (right instanceof ColumnRef rightCol) {
            rightPart = buildColumnSql(rightCol, queryModel);
        } else if (right instanceof DimensionProxy rightProxy) {
            rightPart = buildColumnSql(rightProxy.toColumnRef(), queryModel);
        } else {
            // 常量值使用占位符
            rightPart = "?";
        }

        return leftPart + " " + operator + " " + rightPart;
    }

    /**
     * 构建字段的 SQL 表达式
     *
     * @param columnRef 字段引用
     * @return SQL 表达式（如 fo.order_id）
     */
    private String buildColumnSql(ColumnRef columnRef) {
        return buildColumnSql(columnRef, null);
    }

    private String buildColumnSql(ColumnRef columnRef, QueryModel queryModel) {
        if (queryModel != null) {
            DbColumn dbColumn = findDbColumn(queryModel, columnRef);
            RX.notNull(dbColumn, "JOIN 条件字段不存在: " + columnRef.getFullRef());
            String alias = queryModel.getAlias(dbColumn.getQueryObject());
            if (queryModel instanceof com.foggyframework.dataset.db.model.spi.JdbcQueryModel jdbcQueryModel) {
                return dbColumn.getDeclare(null, alias, jdbcQueryModel.getDialect());
            }
            return dbColumn.getDeclare(null, alias);
        }

        String alias = columnRef.getTableAlias();
        if (alias != null && !alias.isEmpty()) {
            return alias + "." + columnRef.getColumnName();
        }
        // 如果没有别名，使用模型名（后续加载时会分配别名）
        return columnRef.getColumnName();
    }

    /**
     * 解析 JOIN 条件引用到的运行时列，用于补齐 ON 条件依赖的 JOIN 路径。
     *
     * @param queryModel 查询模型
     * @return 条件中可解析出的列
     */
    public List<DbColumn> resolveReferencedColumns(QueryModel queryModel) {
        if (queryModel == null) {
            return List.of();
        }
        List<DbColumn> columns = new ArrayList<>();
        DbColumn leftColumn = findDbColumn(queryModel, left);
        if (leftColumn != null) {
            columns.add(leftColumn);
        }

        ColumnRef rightRef = getRightAsColumnRef();
        if (rightRef != null) {
            DbColumn rightColumn = findDbColumn(queryModel, rightRef);
            if (rightColumn != null) {
                columns.add(rightColumn);
            }
        }
        return columns;
    }

    private DbColumn findDbColumn(QueryModel queryModel, ColumnRef columnRef) {
        if (queryModel == null || columnRef == null) {
            return null;
        }
        Set<String> candidateNames = new LinkedHashSet<>();
        candidateNames.add(columnRef.getFullRef());
        candidateNames.add(columnRef.getAliasRef());
        candidateNames.add(columnRef.getColumnName());

        String modelName = columnRef.getModelName();
        String tableAlias = columnRef.getTableAlias();
        if (queryModel.getJdbcModelList() != null) {
            for (TableModel tableModel : queryModel.getJdbcModelList()) {
                if (!modelName.equals(tableModel.getName())) {
                    continue;
                }
                if (tableAlias != null && !tableAlias.isEmpty()
                        && !tableAlias.equals(tableModel.getAlias())) {
                    continue;
                }
                for (String candidateName : candidateNames) {
                    DbColumn dbColumn = tableModel.findJdbcColumnByName(candidateName);
                    if (dbColumn != null) {
                        return dbColumn;
                    }
                }
            }
        }

        for (String candidateName : candidateNames) {
            DbColumn dbColumn = queryModel.findJdbcColumn(candidateName);
            if (dbColumn != null) {
                return dbColumn;
            }
        }
        return null;
    }

    /**
     * 获取常量参数值（如果右侧是常量）
     *
     * @return 常量值，如果右侧是 ColumnRef 则返回 null
     */
    public Object getConstantValue() {
        return isRightColumnRef() ? null : right;
    }

    @Override
    public String toString() {
        return toSqlFragment();
    }
}
