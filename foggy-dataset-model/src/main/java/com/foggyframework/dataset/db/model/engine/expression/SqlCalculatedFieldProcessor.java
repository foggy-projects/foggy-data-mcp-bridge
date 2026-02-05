package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlBinaryExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlColumnRefExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlFunctionExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlUnaryExp;
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

import java.util.*;

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

        // 按依赖关系排序
        List<CalculatedFieldDef> sortedFields = sortByDependencies(calculatedFields);

        List<CalculatedDbColumn> result = new ArrayList<>(sortedFields.size());

        for (CalculatedFieldDef fieldDef : sortedFields) {
            CalculatedDbColumn column = doProcessCalculatedField(fieldDef, appCtx);
            result.add(column);
        }

        return result;
    }

    /**
     * 按依赖关系对计算字段进行拓扑排序
     * <p>
     * 确保被引用的字段先于引用它的字段被处理。
     * 支持内联表达式别名和用户定义的 calculatedFields 混合引用。
     * </p>
     *
     * @param calculatedFields 计算字段定义列表
     * @return 排序后的计算字段列表
     * @throws IllegalArgumentException 如果检测到循环引用
     */
    private List<CalculatedFieldDef> sortByDependencies(List<CalculatedFieldDef> calculatedFields) {
        if (calculatedFields.size() <= 1) {
            return new ArrayList<>(calculatedFields);
        }

        // 1. 收集所有字段名
        Set<String> allFieldNames = new HashSet<>();
        Map<String, CalculatedFieldDef> fieldMap = new LinkedHashMap<>();
        for (CalculatedFieldDef field : calculatedFields) {
            allFieldNames.add(field.getName());
            fieldMap.put(field.getName(), field);
        }

        // 2. 分析每个字段的依赖（只关心对其他 calculatedField 的依赖）
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (CalculatedFieldDef field : calculatedFields) {
            // 确保表达式已编译
            Exp compiledExp = field.getCompiledExp();
            if (compiledExp == null && field.getExpression() != null) {
                compiledExp = compileExpression(field.getExpression());
                field.setCompiledExp(compiledExp);
            }

            // 提取依赖
            Set<String> refs = new HashSet<>();
            if (compiledExp != null) {
                extractColumnReferences(compiledExp, refs);
            }

            // 只保留对其他 calculatedField 的依赖
            refs.retainAll(allFieldNames);
            // 移除自引用
            refs.remove(field.getName());

            dependencies.put(field.getName(), refs);

            if (log.isDebugEnabled() && !refs.isEmpty()) {
                log.debug("Field '{}' depends on: {}", field.getName(), refs);
            }
        }

        // 3. 拓扑排序（Kahn's algorithm）
        List<CalculatedFieldDef> sorted = new ArrayList<>(calculatedFields.size());

        // 找出不依赖其他 calculatedField 的字段（出度为 0）
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            if (entry.getValue().isEmpty()) {
                queue.add(entry.getKey());
            }
        }

        Set<String> processed = new HashSet<>();

        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (processed.contains(name)) {
                continue;
            }
            processed.add(name);
            sorted.add(fieldMap.get(name));

            // 找出依赖这个字段的其他字段，检查它们的依赖是否都已处理
            for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
                if (processed.contains(entry.getKey())) {
                    continue;
                }
                Set<String> deps = entry.getValue();
                if (deps.contains(name)) {
                    // 检查是否所有依赖都已处理
                    boolean allDepsProcessed = true;
                    for (String dep : deps) {
                        if (!processed.contains(dep)) {
                            allDepsProcessed = false;
                            break;
                        }
                    }
                    if (allDepsProcessed) {
                        queue.add(entry.getKey());
                    }
                }
            }
        }

        // 4. 检测循环引用
        if (sorted.size() < calculatedFields.size()) {
            // 找出循环引用的字段
            Set<String> cycleFields = new LinkedHashSet<>(fieldMap.keySet());
            cycleFields.removeAll(processed);
            throw new IllegalArgumentException(
                    "检测到计算字段循环引用，涉及字段: " + cycleFields +
                    "。请检查这些字段的表达式，确保没有互相引用。");
        }

        if (log.isDebugEnabled()) {
            List<String> sortedNames = new ArrayList<>();
            for (CalculatedFieldDef f : sorted) {
                sortedNames.add(f.getName());
            }
            log.debug("Calculated fields sorted by dependencies: {}", sortedNames);
        }

        return sorted;
    }

    /**
     * 从 AST 中递归提取所有列引用
     *
     * @param exp  表达式 AST
     * @param refs 收集到的列名集合
     */
    private void extractColumnReferences(Exp exp, Set<String> refs) {
        if (exp == null) {
            return;
        }

        // 处理包装器类型（SqlExpWrapper, SqlExpFunCallWrapper 等）
        if (exp instanceof SqlExpHolder) {
            extractColumnReferences(((SqlExpHolder) exp).getInnerSqlExp(), refs);
            return;
        }

        if (exp instanceof SqlColumnRefExp) {
            refs.add(((SqlColumnRefExp) exp).getColumnName());
        } else if (exp instanceof SqlBinaryExp) {
            SqlBinaryExp binary = (SqlBinaryExp) exp;
            extractColumnReferences(binary.getLeft(), refs);
            extractColumnReferences(binary.getRight(), refs);
        } else if (exp instanceof SqlUnaryExp) {
            extractColumnReferences(((SqlUnaryExp) exp).getOperand(), refs);
        } else if (exp instanceof SqlFunctionExp) {
            for (Exp arg : ((SqlFunctionExp) exp).getArgs()) {
                extractColumnReferences(arg, refs);
            }
        }
        // SqlLiteralExp 不包含列引用，忽略
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
