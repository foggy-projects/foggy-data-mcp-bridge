package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
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
 * 计算字段服务（工具类）
 * <p>
 * 负责编译和处理动态计算字段，将表达式转换为 SQL 片段。
 * 使用静态方法提供服务，无需实例化。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * SqlExpContext context = new SqlExpContext(queryModel, dialect, appCtx);
 * List&lt;CalculatedJdbcColumn&gt; columns = CalculatedFieldService.processCalculatedFields(calculatedFields, context, appCtx);
 * </pre>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public final class CalculatedFieldService {

    /**
     * 共享的表达式解析器（线程安全）
     */
    private static final Parser SHARED_PARSER;

    static {
        SqlExpFactory expFactory = new SqlExpFactory();
        SHARED_PARSER = ParserFactory.newInstance().newExpParser(expFactory);
    }

    /**
     * 私有构造函数，防止实例化
     */
    private CalculatedFieldService() {
    }

    /**
     * 处理计算字段列表
     * <p>
     * 按顺序编译每个计算字段，支持后面的字段引用前面的字段。
     * </p>
     *
     * @param calculatedFields 计算字段定义列表
     * @param context          SQL 表达式上下文
     * @param appCtx           Spring 上下文
     * @return 计算字段列列表
     */
    public static List<CalculatedDbColumn> processCalculatedFields(
            List<CalculatedFieldDef> calculatedFields,
            SqlExpContext context,
            ApplicationContext appCtx) {
        if (calculatedFields == null || calculatedFields.isEmpty()) {
            return new ArrayList<>();
        }

        List<CalculatedDbColumn> result = new ArrayList<>(calculatedFields.size());

        for (CalculatedFieldDef fieldDef : calculatedFields) {
            CalculatedDbColumn column = processCalculatedField(fieldDef, context, appCtx);
            result.add(column);
        }

        return result;
    }

    /**
     * 处理单个计算字段
     *
     * @param fieldDef 计算字段定义
     * @param context  SQL 表达式上下文
     * @param appCtx   Spring 上下文
     * @return 计算字段列
     */
    public static CalculatedDbColumn processCalculatedField(
            CalculatedFieldDef fieldDef,
            SqlExpContext context,
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
            // 如果 InlineExpressionPreprocessStep 已经预编译，则复用
            Exp compiledExp = fieldDef.getCompiledExp();
            if (compiledExp == null) {
                compiledExp = compileExpression(fieldDef.getExpression());
                fieldDef.setCompiledExp(compiledExp);
            } else if (log.isDebugEnabled()) {
                log.debug("Reusing pre-compiled AST for field: {}", fieldDef.getName());
            }

            // 2. 执行表达式得到 SQL 片段
            SqlFragment sqlFragment = evaluateExpression(compiledExp, context, appCtx);

            // 2.1 如果 InlineExpressionPreprocessStep 推断了聚合类型，传递到 SqlFragment
            //     注意：不设置 hasAggregate，因为表达式本身没有聚合函数
            //     Engine 层会根据 aggregationType 来包裹聚合函数
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
            // 重新抛出安全异常
            throw e;
        } catch (Exception e) {
            String errorMsg = "编译计算字段表达式失败 [" + fieldDef.getName() + "]: " + e.getMessage();
            throw RX.throwAUserTip(errorMsg, errorMsg, null, e);
        }
    }

    /**
     * 编译表达式字符串
     * <p>
     * 使用共享的 Parser 实例编译表达式，可被其他类复用。
     * </p>
     *
     * @param expression 表达式字符串
     * @return 编译后的 AST
     * @throws RuntimeException 如果表达式语法错误
     */
    public static Exp compileExpression(String expression) {
        try {
            // 使用 compileEl 解析纯 fsscript 表达式
            // compile 是为 SQL 模板语法设计的（如 select ... where ${expr}），会把标识符当作字面量
            Exp exp = SHARED_PARSER.compileEl(null, expression);
            if (log.isDebugEnabled()) {
                log.debug("Compiled expression '{}' -> AST type: {}, AST: {}",
                        expression, exp.getClass().getName(), exp);
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
     *
     * @param exp     编译后的表达式
     * @param context SQL 表达式上下文
     * @param appCtx  Spring 上下文
     * @return SQL 片段
     */
    private static SqlFragment evaluateExpression(Exp exp, SqlExpContext context, ApplicationContext appCtx) {
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

        // 如果结果是字符串，可能是因为解析器返回了原始表达式字符串
        // 这种情况下我们需要检查表达式 AST 是否正确创建
        log.warn("Expression did not return SqlFragment, got: {} (type: {})",
                result, result != null ? result.getClass().getName() : "null");

        throw new RuntimeException("表达式执行结果不是 SqlFragment: " + result +
                " (type: " + (result != null ? result.getClass().getName() : "null") + ")");
    }
}
