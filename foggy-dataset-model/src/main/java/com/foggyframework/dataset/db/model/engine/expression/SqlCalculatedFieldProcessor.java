package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.WindowOrderDef;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.support.CalculatedDbColumn;
import com.foggyframework.fsscript.parser.spi.Exp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.*;

/**
 * SQL 计算字段处理器
 * <p>
 * 用于 JDBC/SQL 类型的 QueryModel，将计算字段表达式编译为 SQL 片段。
 * 共享逻辑（拓扑排序、表达式编译/求值）委托给 {@link CalculatedFieldService}，
 * 本类仅负责窗口函数包装等 SQL 特有能力。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public class SqlCalculatedFieldProcessor implements CalculatedFieldProcessor {

    private final JdbcQueryModel queryModel;
    private final FDialect dialect;
    private SqlExpContext context;
    private CalculateQueryContext calculateQueryContext;

    public SqlCalculatedFieldProcessor(JdbcQueryModel queryModel, FDialect dialect) {
        this.queryModel = queryModel;
        this.dialect = dialect;
    }

    @Override
    public List<CalculatedDbColumn> processCalculatedFields(
            List<CalculatedFieldDef> calculatedFields,
            ApplicationContext appCtx) {
        if (calculatedFields == null || calculatedFields.isEmpty()) {
            return new ArrayList<>();
        }

        // 创建 SQL 表达式上下文
        this.context = new SqlExpContext(queryModel, dialect, appCtx);
        this.context.setCalculateQueryContext(calculateQueryContext);

        // 按依赖关系排序（委托 CalculatedFieldService）
        List<CalculatedFieldDef> sortedFields = CalculatedFieldService.sortByDependencies(calculatedFields);

        List<CalculatedDbColumn> result = new ArrayList<>(sortedFields.size());

        for (CalculatedFieldDef fieldDef : sortedFields) {
            CalculatedDbColumn column = doProcessCalculatedField(fieldDef, appCtx);
            result.add(column);
        }

        return result;
    }

    @Override
    public CalculatedDbColumn processCalculatedField(
            CalculatedFieldDef fieldDef,
            ApplicationContext appCtx) {
        // 如果上下文不存在，创建一个新的
        if (this.context == null) {
            this.context = new SqlExpContext(queryModel, dialect, appCtx);
            this.context.setCalculateQueryContext(calculateQueryContext);
        }
        return doProcessCalculatedField(fieldDef, appCtx);
    }

    /**
     * 处理单个计算字段的内部实现
     * <p>
     * 相比 {@link CalculatedFieldService#processCalculatedField}，额外支持窗口函数（PARTITION BY / ORDER BY / frame）。
     * </p>
     */
    private CalculatedDbColumn doProcessCalculatedField(
            CalculatedFieldDef fieldDef,
            ApplicationContext appCtx) {
        // 验证必填字段
        RX.hasText(fieldDef.getName(), "计算字段名称不能为空");
        RX.hasText(fieldDef.getExpression(), "计算字段表达式不能为空: " + fieldDef.getName());

        // 检查名称是否已存在
        if (context.hasColumn(fieldDef.getName())) {
            throw RX.throwAUserTip("计算字段名称已存在: " + fieldDef.getName());
        }

        try {
            // 1. 获取或编译表达式 AST（委托 CalculatedFieldService）
            Exp compiledExp = fieldDef.getCompiledExp();
            if (compiledExp == null) {
                compiledExp = CalculatedFieldService.compileExpression(fieldDef.getExpression());
                fieldDef.setCompiledExp(compiledExp);
            } else if (log.isDebugEnabled()) {
                log.debug("Reusing pre-compiled AST for field: {}", fieldDef.getName());
            }
            CalculateExpressionAnalyzer.validate(compiledExp);

            // 2. 执行表达式得到 SQL 片段（委托 CalculatedFieldService）
            SqlFragment sqlFragment = CalculatedFieldService.evaluateExpression(compiledExp, context, appCtx);

            // 2.1 如果有 partitionBy/windowOrderBy，包装窗口子句
            if (fieldDef.getPartitionBy() != null || fieldDef.getWindowOrderBy() != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Window clause for {}: partitionBy={}, windowOrderBy={}, windowFrame={}",
                            fieldDef.getName(), fieldDef.getPartitionBy(),
                            fieldDef.getWindowOrderBy(), fieldDef.getWindowFrame());
                }
                sqlFragment = wrapWithWindowClause(sqlFragment, fieldDef, context, appCtx);
            }

            // 2.2 如果推断了聚合类型，传递到 SqlFragment
            if (fieldDef.getAgg() != null && sqlFragment.getAggregationType() == null && !sqlFragment.isHasWindow()) {
                sqlFragment.setAggregationType(fieldDef.getAgg().toUpperCase());
                if (log.isDebugEnabled()) {
                    log.debug("Applied inferred aggregation from CalculatedFieldDef: {} -> agg={}",
                            fieldDef.getName(), fieldDef.getAgg());
                }
            }
            sqlFragment = CalculatedFieldService.applyEmptyDefault(sqlFragment, fieldDef);

            // 3. 创建 CalculatedJdbcColumn
            String caption = StringUtils.isNotEmpty(fieldDef.getCaption()) ? fieldDef.getCaption() : fieldDef.getName();
            CalculatedDbColumn column = new CalculatedDbColumn(
                    fieldDef.getName(),
                    caption,
                    sqlFragment,
                    fieldDef.getDescription()
            );

            // 4. 注册到上下文（支持后续字段引用）
            context.registerCalculatedColumn(fieldDef.getName(), column);

            if (log.isDebugEnabled()) {
                log.debug("Processed calculated field: {} = {} (hasAggregate={})",
                        fieldDef.getName(), sqlFragment.getSql(), sqlFragment.isHasAggregate());
            }

            return column;

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = "编译计算字段表达式失败 [" + fieldDef.getName() + "]: " + e.getMessage();
            throw RX.throwAUserTip(errorMsg, errorMsg, null, e);
        }
    }

    /**
     * 包装窗口函数 OVER 子句
     * <p>
     * 将已编译的 SQL 表达式追加 OVER (PARTITION BY ... ORDER BY ... frame) 子句。
     * partitionBy/windowOrderBy 中的列名会被解析为实际的 SQL 引用。
     * </p>
     *
     * @param baseSql  基础 SQL 片段（如 "RANK()" 或 "AVG(m1.amount)"）
     * @param fieldDef 计算字段定义（含 partitionBy/windowOrderBy/windowFrame）
     * @param context  SQL 表达式上下文
     * @param appCtx   Spring ApplicationContext
     * @return 包含完整 OVER 子句的 SqlFragment
     */
    private SqlFragment wrapWithWindowClause(SqlFragment baseSql, CalculatedFieldDef fieldDef,
                                              SqlExpContext context, ApplicationContext appCtx) {
        StringBuilder overClause = new StringBuilder();
        Set<DbQueryColumn> refs = new LinkedHashSet<>(baseSql.getReferencedColumns());

        // PARTITION BY
        if (fieldDef.getPartitionBy() != null && !fieldDef.getPartitionBy().isEmpty()) {
            overClause.append("PARTITION BY ");
            List<String> partitionSqls = new ArrayList<>();
            for (String colName : fieldDef.getPartitionBy()) {
                DbQueryColumn col = context.resolveColumn(colName);
                String alias = context.getAlias(col);
                String colSql = resolveColumnSql(col, alias, appCtx);
                partitionSqls.add(colSql);
                refs.add(col);
            }
            overClause.append(String.join(", ", partitionSqls));
        }

        // ORDER BY
        if (fieldDef.getWindowOrderBy() != null && !fieldDef.getWindowOrderBy().isEmpty()) {
            if (overClause.length() > 0) {
                overClause.append(" ");
            }
            overClause.append("ORDER BY ");
            List<String> orderSqls = new ArrayList<>();
            for (WindowOrderDef orderDef : fieldDef.getWindowOrderBy()) {
                DbQueryColumn col = context.resolveColumn(orderDef.getField());
                String alias = context.getAlias(col);
                String colSql = resolveColumnSql(col, alias, appCtx);
                orderSqls.add(colSql + " " + orderDef.getNormalizedDir());
                refs.add(col);
            }
            overClause.append(String.join(", ", orderSqls));
        }

        // Window frame
        if (StringUtils.isNotEmpty(fieldDef.getWindowFrame())) {
            if (overClause.length() > 0) {
                overClause.append(" ");
            }
            overClause.append(fieldDef.getWindowFrame());
        }

        return SqlFragment.windowFunction(
                baseSql.getSql(),
                overClause.toString(),
                refs,
                baseSql.getInferredType()
        );
    }

    /**
     * 解析列的 SQL 表达式
     */
    private String resolveColumnSql(DbQueryColumn col, String alias, ApplicationContext appCtx) {
        if (col instanceof CalculatedDbColumn) {
            return ((CalculatedDbColumn) col).getDeclare();
        }
        if (col.getSelectColumn() != null) {
            return col.getSelectColumn().getDeclare(appCtx, alias);
        }
        return alias != null ? alias + "." + col.getAlias() : col.getAlias();
    }

    /**
     * 获取 SQL 表达式上下文
     */
    public SqlExpContext getContext() {
        return context;
    }

    public void setCalculateQueryContext(CalculateQueryContext calculateQueryContext) {
        this.calculateQueryContext = calculateQueryContext;
        if (this.context != null) {
            this.context.setCalculateQueryContext(calculateQueryContext);
        }
    }
}
