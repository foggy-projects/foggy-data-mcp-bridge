package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.spi.CalculatedFieldProcessor;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.support.CalculatedDbColumn;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Parser;
import com.foggyframework.fsscript.parser.spi.ParserFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 计算字段处理器
 * <p>
 * 用于 JDBC/SQL 类型的 QueryModel，将计算字段表达式编译为 SQL 片段。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public class SqlCalculatedFieldProcessor implements CalculatedFieldProcessor {

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
    private SqlExpContext context;

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

        List<CalculatedDbColumn> result = new ArrayList<>(calculatedFields.size());

        for (CalculatedFieldDef fieldDef : calculatedFields) {
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
        }
        return doProcessCalculatedField(fieldDef, appCtx);
    }

    /**
     * 处理单个计算字段的内部实现
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
            // 1. 获取或编译表达式 AST
            Exp compiledExp = fieldDef.getCompiledExp();
            if (compiledExp == null) {
                compiledExp = compileExpression(fieldDef.getExpression());
                fieldDef.setCompiledExp(compiledExp);
            } else if (log.isDebugEnabled()) {
                log.debug("Reusing pre-compiled AST for field: {}", fieldDef.getName());
            }

            // 2. 执行表达式得到 SQL 片段
            SqlFragment sqlFragment = evaluateExpression(compiledExp, context, appCtx);

            // 2.1 如果推断了聚合类型，传递到 SqlFragment
            if (fieldDef.getAgg() != null && sqlFragment.getAggregationType() == null) {
                sqlFragment.setAggregationType(fieldDef.getAgg().toUpperCase());
                if (log.isDebugEnabled()) {
                    log.debug("Applied inferred aggregation from CalculatedFieldDef: {} -> agg={}",
                            fieldDef.getName(), fieldDef.getAgg());
                }
            }

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
     * 编译表达式字符串
     */
    private Exp compileExpression(String expression) {
        try {
            Exp exp = SHARED_PARSER.compileEl(null, expression);
            if (log.isDebugEnabled()) {
                log.debug("Compiled expression '{}' -> AST type: {}", expression, exp.getClass().getName());
            }
            return exp;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("表达式语法错误: " + e.getMessage(), e);
        }
    }

    /**
     * 执行表达式得到 SQL 片段
     */
    private SqlFragment evaluateExpression(Exp exp, SqlExpContext context, ApplicationContext appCtx) {
        ExpEvaluator evaluator = DefaultExpEvaluator.newInstance(appCtx);
        evaluator.setVar(SqlExpContext.CONTEXT_KEY, context);

        Object result = exp.evalResult(evaluator);

        if (log.isDebugEnabled()) {
            log.debug("Expression evalResult type: {}, value: {}",
                    result != null ? result.getClass().getName() : "null", result);
        }

        if (result instanceof SqlFragment) {
            return (SqlFragment) result;
        }

        log.warn("Expression did not return SqlFragment, got: {} (type: {})",
                result, result != null ? result.getClass().getName() : "null");

        throw new RuntimeException("表达式执行结果不是 SqlFragment: " + result +
                " (type: " + (result != null ? result.getClass().getName() : "null") + ")");
    }

    /**
     * 获取 SQL 表达式上下文
     */
    public SqlExpContext getContext() {
        return context;
    }
}
