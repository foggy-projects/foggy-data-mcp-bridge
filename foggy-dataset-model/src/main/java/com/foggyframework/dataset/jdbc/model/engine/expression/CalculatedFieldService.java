package com.foggyframework.dataset.jdbc.model.engine.expression;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.jdbc.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.jdbc.model.spi.support.CalculatedJdbcColumn;
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
 * 计算字段服务
 * <p>
 * 负责编译和处理动态计算字段，将表达式转换为 SQL 片段。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * CalculatedFieldService service = new CalculatedFieldService(queryModel, dialect, appCtx);
 * List&lt;CalculatedJdbcColumn&gt; columns = service.processCalculatedFields(calculatedFields);
 * </pre>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public class CalculatedFieldService {

    private final JdbcQueryModel queryModel;
    private final FDialect dialect;
    private final ApplicationContext appCtx;
    private final Parser parser;
    private final SqlExpContext context;

    /**
     * 创建计算字段服务
     *
     * @param queryModel 查询模型
     * @param dialect    数据库方言
     * @param appCtx     Spring 上下文
     */
    public CalculatedFieldService(JdbcQueryModel queryModel, FDialect dialect, ApplicationContext appCtx) {
        this.queryModel = queryModel;
        this.dialect = dialect;
        this.appCtx = appCtx;
        this.context = new SqlExpContext(queryModel, dialect, appCtx);

        // 创建使用 SqlExpFactory 的解析器
        SqlExpFactory expFactory = new SqlExpFactory();
        this.parser = ParserFactory.newInstance().newExpParser(expFactory);
    }

    /**
     * 处理计算字段列表
     * <p>
     * 按顺序编译每个计算字段，支持后面的字段引用前面的字段。
     * </p>
     *
     * @param calculatedFields 计算字段定义列表
     * @return 计算字段列列表
     */
    public List<CalculatedJdbcColumn> processCalculatedFields(List<CalculatedFieldDef> calculatedFields) {
        if (calculatedFields == null || calculatedFields.isEmpty()) {
            return new ArrayList<>();
        }

        List<CalculatedJdbcColumn> result = new ArrayList<>(calculatedFields.size());

        for (CalculatedFieldDef fieldDef : calculatedFields) {
            CalculatedJdbcColumn column = processCalculatedField(fieldDef);
            result.add(column);
        }

        return result;
    }

    /**
     * 处理单个计算字段
     *
     * @param fieldDef 计算字段定义
     * @return 计算字段列
     */
    public CalculatedJdbcColumn processCalculatedField(CalculatedFieldDef fieldDef) {
        // 验证必填字段
        RX.hasText(fieldDef.getName(), "计算字段名称不能为空");
        RX.hasText(fieldDef.getExpression(), "计算字段表达式不能为空: " + fieldDef.getName());

        // 检查名称是否已存在
        if (context.hasColumn(fieldDef.getName())) {
            throw RX.throwAUserTip("计算字段名称已存在: " + fieldDef.getName());
        }

        try {
            // 1. 编译表达式
            Exp compiledExp = compileExpression(fieldDef.getExpression());
            fieldDef.setCompiledExp(compiledExp);

            // 2. 执行表达式得到 SQL 片段
            SqlFragment sqlFragment = evaluateExpression(compiledExp);

            // 3. 创建 CalculatedJdbcColumn
            String caption = StringUtils.isNotEmpty(fieldDef.getCaption()) ? fieldDef.getCaption() : fieldDef.getName();
            CalculatedJdbcColumn column = new CalculatedJdbcColumn(
                    fieldDef.getName(),
                    caption,
                    sqlFragment,
                    fieldDef.getDescription()
            );

            // 4. 注册到上下文（支持后续字段引用）
            context.registerCalculatedColumn(fieldDef.getName(), column);

            if (log.isDebugEnabled()) {
                log.debug("Processed calculated field: {} = {}", fieldDef.getName(), sqlFragment.getSql());
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
     *
     * @param expression 表达式字符串
     * @return 编译后的 AST
     */
    private Exp compileExpression(String expression) {
        try {
            // 使用 compileEl 解析纯 fsscript 表达式
            // compile 是为 SQL 模板语法设计的（如 select ... where ${expr}），会把标识符当作字面量
            Exp exp = parser.compileEl(null, expression);
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
     * @param exp 编译后的表达式
     * @return SQL 片段
     */
    private SqlFragment evaluateExpression(Exp exp) {
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

    /**
     * 获取上下文（用于后续处理）
     *
     * @return SQL 表达式上下文
     */
    public SqlExpContext getContext() {
        return context;
    }
}
