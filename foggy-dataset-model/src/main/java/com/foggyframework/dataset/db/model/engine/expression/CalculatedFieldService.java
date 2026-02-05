package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlBinaryExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlColumnRefExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlFunctionExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlUnaryExp;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpHolder;
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
     * 自动分析字段间的依赖关系，按依赖顺序编译计算字段。
     * 支持计算字段引用其他计算字段（包括内联表达式生成的）。
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

        // 按依赖关系排序
        List<CalculatedFieldDef> sortedFields = sortByDependencies(calculatedFields);

        List<CalculatedDbColumn> result = new ArrayList<>(sortedFields.size());

        for (CalculatedFieldDef fieldDef : sortedFields) {
            CalculatedDbColumn column = processCalculatedField(fieldDef, context, appCtx);
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
    private static List<CalculatedFieldDef> sortByDependencies(List<CalculatedFieldDef> calculatedFields) {
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

        // 计算入度
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String name : fieldMap.keySet()) {
            inDegree.put(name, 0);
        }
        for (Set<String> deps : dependencies.values()) {
            for (String dep : deps) {
                if (inDegree.containsKey(dep)) {
                    inDegree.put(dep, inDegree.get(dep) + 1);
                }
            }
        }

        // 找出入度为 0 的节点（没有被其他字段依赖）
        // 注意：我们要按"被依赖的先处理"，所以入度为 0 表示没有字段依赖它
        // 但我们需要的是"依赖其他字段少的先处理"，所以应该计算出度
        // 重新思考：入度 = 有多少字段依赖我，出度 = 我依赖多少字段
        // 我们需要先处理"不依赖其他字段"的，即出度为 0 的

        // 重新计算：使用出度（依赖数量）
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
    private static void extractColumnReferences(Exp exp, Set<String> refs) {
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
