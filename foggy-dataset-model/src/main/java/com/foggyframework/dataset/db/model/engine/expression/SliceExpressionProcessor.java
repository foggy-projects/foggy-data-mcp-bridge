package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Parser;
import com.foggyframework.fsscript.parser.spi.ParserFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

/**
 * Slice 条件表达式处理器
 * <p>
 * 用于处理 slice 中的 $expr 表达式条件和 $field 字段引用。
 * 复用现有的表达式解析引擎 ({@link SqlExpFactory})。
 * </p>
 *
 * <h3>支持的语法</h3>
 * <ul>
 *   <li>$expr: 完整表达式条件，如 {@code "actualAmount > budgetAmount * 1.1"}</li>
 *   <li>$field: 字段引用作为 value，如 {@code {"field": "a", "op": ">", "value": {"$field": "b"}}}</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 8.3.0
 */
@Slf4j
public class SliceExpressionProcessor {

    /**
     * 共享的表达式解析器（线程安全）
     */
    private static final Parser SHARED_PARSER;

    static {
        SqlExpFactory expFactory = new SqlExpFactory();
        SHARED_PARSER = ParserFactory.newInstance().newExpParser(expFactory);
    }

    private final JdbcQueryModel queryModel;
    private final FDialect dialect;
    private final ApplicationContext appCtx;
    private SqlExpContext context;

    public SliceExpressionProcessor(JdbcQueryModel queryModel, FDialect dialect, ApplicationContext appCtx) {
        this.queryModel = queryModel;
        this.dialect = dialect;
        this.appCtx = appCtx;
    }

    /**
     * 处理 $expr 表达式条件
     * <p>
     * 将表达式编译为 SQL 条件片段。
     * </p>
     *
     * @param expression 表达式字符串，如 "actualAmount > budgetAmount"
     * @return SQL 条件字符串
     */
    public String processExpression(String expression) {
        if (expression == null || expression.isEmpty()) {
            throw RX.throwAUserTip("$expr 表达式不能为空");
        }

        try {
            // 确保上下文已初始化
            ensureContext();

            // 编译表达式
            Exp compiledExp = SHARED_PARSER.compileEl(null, expression);

            // 执行表达式得到 SQL 片段
            ExpEvaluator evaluator = DefaultExpEvaluator.newInstance(appCtx);
            evaluator.setVar(SqlExpContext.CONTEXT_KEY, context);

            Object result = compiledExp.evalResult(evaluator);

            if (result instanceof SqlFragment) {
                SqlFragment fragment = (SqlFragment) result;
                String sql = fragment.getSql();

                if (log.isDebugEnabled()) {
                    log.debug("Processed $expr '{}' -> SQL: {}", expression, sql);
                }

                return sql;
            }

            throw RX.throwAUserTip("$expr 表达式执行结果无效: " + expression);

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = "处理 $expr 表达式失败 [" + expression + "]: " + e.getMessage();
            log.error(errorMsg, e);
            throw RX.throwAUserTip(errorMsg, errorMsg, null, e);
        }
    }

    /**
     * 处理 $field 字段引用
     * <p>
     * 将字段引用转换为带别名的 SQL 列名。
     * </p>
     *
     * @param sliceDef 条件定义
     * @param tableAlias 表别名
     * @return SQL 条件字符串，如 "t1.actual_amount > t1.budget_amount"
     */
    public String processFieldReference(CondRequestDef sliceDef, String tableAlias) {
        String leftField = sliceDef.getField();
        String rightField = sliceDef._getReferencedField();
        String op = sliceDef.getOp();

        if (leftField == null || leftField.isEmpty()) {
            throw RX.throwAUserTip("$field 引用的左侧字段不能为空");
        }
        if (rightField == null || rightField.isEmpty()) {
            throw RX.throwAUserTip("$field 引用的右侧字段不能为空");
        }
        if (op == null || op.isEmpty()) {
            throw RX.throwAUserTip("$field 引用的操作符不能为空");
        }

        // 解析左侧字段
        String leftColumnSql = resolveColumnSql(leftField, tableAlias);

        // 解析右侧字段
        String rightColumnSql = resolveColumnSql(rightField, tableAlias);

        // 构建 SQL
        String sql = leftColumnSql + " " + normalizeOperator(op) + " " + rightColumnSql;

        if (log.isDebugEnabled()) {
            log.debug("Processed $field reference: {} {} {} -> SQL: {}",
                    leftField, op, rightField, sql);
        }

        return sql;
    }

    /**
     * 解析字段名为 SQL 列表达式
     *
     * @param fieldName 字段名
     * @param defaultAlias 默认表别名
     * @return SQL 列表达式
     */
    private String resolveColumnSql(String fieldName, String defaultAlias) {
        // 先尝试从 QueryModel 查找列
        DbColumn column = queryModel.findJdbcColumnForCond(fieldName, false, false);

        if (column != null) {
            // 获取表别名
            String alias = queryModel.getAlias(column.getQueryObject());
            if (alias == null) {
                alias = defaultAlias;
            }

            // 返回带别名的列名
            if (alias != null && !alias.isEmpty()) {
                return alias + "." + column.getSqlColumn().getName();
            }
            return column.getSqlColumn().getName();
        }

        // 如果找不到列，尝试作为原始列名处理
        if (defaultAlias != null && !defaultAlias.isEmpty()) {
            return defaultAlias + "." + fieldName;
        }

        return fieldName;
    }

    /**
     * 规范化操作符
     */
    private String normalizeOperator(String op) {
        if (op == null) {
            return "=";
        }
        switch (op.toLowerCase()) {
            case "eq":
                return "=";
            case "ne":
            case "<>":
                return "!=";
            case "gt":
                return ">";
            case "gte":
                return ">=";
            case "lt":
                return "<";
            case "lte":
                return "<=";
            default:
                return op;
        }
    }

    /**
     * 确保上下文已初始化
     */
    private void ensureContext() {
        if (this.context == null) {
            this.context = new SqlExpContext(queryModel, dialect, appCtx);
        }
    }

    /**
     * 获取 SQL 表达式上下文
     */
    public SqlExpContext getContext() {
        return context;
    }
}
