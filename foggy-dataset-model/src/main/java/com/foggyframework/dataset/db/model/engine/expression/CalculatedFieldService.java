package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlBinaryExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlCalculateExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlColumnRefExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlFunctionExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlListExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlRemoveExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlUnaryExp;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpHolder;
import com.foggyframework.dataset.db.model.spi.support.CalculatedDbColumn;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.FsscriptDialect;
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
    static List<CalculatedFieldDef> sortByDependencies(List<CalculatedFieldDef> calculatedFields) {
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

        // 找出不依赖其他 calculatedField 的字段（依赖集为空）
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
     * 从表达式字符串中提取所有引用的列名
     * <p>
     * 编译表达式为 AST，然后递归提取所有 {@link SqlColumnRefExp} 引用。
     * 用于列权限校验场景：判断表达式依赖哪些源字段。
     * </p>
     *
     * @param expression 表达式字符串，如 {@code "a + b"}、{@code "SUM(amount * rate)"}
     * @return 引用的列名集合（不可变），如 {@code {"a", "b"}} 或 {@code {"amount", "rate"}}；
     *         表达式为空时返回空集合
     * @throws RuntimeException 如果表达式语法错误
     * @since 8.2.0
     */
    public static Set<String> extractColumnReferences(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Exp compiled = compileExpression(expression);
        Set<String> refs = new LinkedHashSet<>();
        extractColumnReferences(compiled, refs);
        return Collections.unmodifiableSet(refs);
    }

    /**
     * 将表达式的列引用展开为基础字段（传递解析计算字段依赖）
     * <p>
     * 对于表达式中引用的列名，如果该列名是 {@code calculatedFieldMap} 中的计算字段，
     * 则递归展开为该计算字段的基础字段依赖。最终返回的集合只包含基础字段名，
     * 不包含任何计算字段名。
     * </p>
     * <p>
     * 用于列权限校验场景：{@code fieldAccess} 白名单只包含基础字段名，
     * 计算字段引用其他计算字段时需要传递展开才能正确判定。
     * </p>
     *
     * @param expression         表达式字符串
     * @param calculatedFieldMap 计算字段名 → 表达式的映射（用于传递解析）
     * @return 展开后的基础字段集合（不可变）
     * @throws RuntimeException 如果表达式语法错误
     * @since 8.2.0
     */
    public static Set<String> resolveBaseColumnReferences(String expression,
                                                           Map<String, String> calculatedFieldMap) {
        if (expression == null || expression.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> directRefs = extractColumnReferences(expression);
        if (calculatedFieldMap == null || calculatedFieldMap.isEmpty()) {
            return directRefs;
        }

        // 传递展开：将引用的计算字段替换为其基础字段
        Set<String> baseRefs = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>(); // 防止循环引用无限递归
        for (String ref : directRefs) {
            resolveToBase(ref, calculatedFieldMap, baseRefs, visited);
        }
        return Collections.unmodifiableSet(baseRefs);
    }

    /**
     * 递归将列引用解析为基础字段
     */
    private static void resolveToBase(String ref, Map<String, String> calculatedFieldMap,
                                       Set<String> baseRefs, Set<String> visited) {
        if (!visited.add(ref)) {
            return; // 循环引用保护
        }
        String calcExpr = calculatedFieldMap.get(ref);
        if (calcExpr == null) {
            // 不是计算字段，是基础字段
            baseRefs.add(ref);
        } else {
            // 是计算字段，递归展开其依赖
            Set<String> innerRefs = extractColumnReferences(calcExpr);
            for (String inner : innerRefs) {
                resolveToBase(inner, calculatedFieldMap, baseRefs, visited);
            }
        }
    }

    /**
     * 从 AST 中递归提取所有列引用
     *
     * @param exp  表达式 AST
     * @param refs 收集到的列名集合
     */
    public static void extractColumnReferences(Exp exp, Set<String> refs) {
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
        } else if (exp instanceof SqlCalculateExp) {
            for (Exp arg : ((SqlCalculateExp) exp).getArgs()) {
                extractColumnReferences(arg, refs);
            }
        } else if (exp instanceof SqlRemoveExp) {
            for (String field : ((SqlRemoveExp) exp).getFieldNames()) {
                refs.add(field);
            }
        } else if (exp instanceof SqlListExp) {
            // IN / NOT IN 的 RHS 列表。典型内容是字面量（SqlLiteralExp 会自动忽略），
            // 但也允许 `x in (col1, col2)` 这种列组合场景，此时 col1 / col2 需要被收为依赖。
            for (Exp item : ((SqlListExp) exp).getItems()) {
                extractColumnReferences(item, refs);
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
        RX.hasText(fieldDef.getName(), "CALCULATED_FIELD_EXPRESSION_INVALID: calculatedFields.name is required.");
        RX.hasText(fieldDef.getExpression(), "CALCULATED_FIELD_EXPRESSION_INVALID: calculatedFields['"
                + fieldDef.getName() + "'].expression is required.");

        // 检查名称是否已存在
        if (context.hasColumn(fieldDef.getName()) && !isInlineAggregateAlias(fieldDef)) {
            throw RX.throwAUserTip("CALCULATED_FIELD_NAME_COLLISION: 计算字段名称已存在: "
                    + fieldDef.getName()
                    + "。请更换 calculatedFields.name，或直接在 columns 中引用已有字段。");
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
            CalculateExpressionAnalyzer.validate(compiledExp);

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
            sqlFragment = applyEmptyDefault(sqlFragment, fieldDef);

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
            String errorMsg = "CALCULATED_FIELD_EXPRESSION_INVALID: 编译计算字段表达式失败 ["
                    + fieldDef.getName() + "]: " + e.getMessage();
            throw RX.throwAUserTip(errorMsg, errorMsg, null, e);
        }
    }

    private static boolean isInlineAggregateAlias(CalculatedFieldDef fieldDef) {
        return fieldDef.getOrigin() == CalculatedFieldDef.Origin.INLINE_EXPRESSION
                && StringUtils.isNotEmpty(fieldDef.getAgg());
    }

    /**
     * 对计算字段应用空值默认值。
     * <p>
     * 当表达式依靠 {@code agg} 推断聚合时，必须先包裹聚合函数，再套 COALESCE，
     * 避免生成 {@code SUM(COALESCE(amount, 0))} 这类改变业务语义的 SQL。
     * </p>
     */
    public static SqlFragment applyEmptyDefault(SqlFragment sqlFragment, CalculatedFieldDef fieldDef) {
        if (sqlFragment == null || fieldDef == null || fieldDef.getEmptyDefault() == null) {
            return sqlFragment;
        }

        String sql = sqlFragment.getSql();
        String aggregationType = sqlFragment.getAggregationType();
        boolean hasAggregate = sqlFragment.isHasAggregate();
        boolean hasWindow = sqlFragment.isHasWindow();

        if (!hasAggregate && !hasWindow && fieldDef.getAgg() != null) {
            aggregationType = fieldDef.getAgg().toUpperCase();
            sql = aggregationType + "(" + sql + ")";
            hasAggregate = true;
        }

        SqlFragment wrapped = SqlFragment.ofLiteral(
                "COALESCE(" + sql + ", " + toSqlLiteral(fieldDef.getEmptyDefault()) + ")",
                sqlFragment.getInferredType()
        );
        wrapped.getReferencedColumns().addAll(sqlFragment.getReferencedColumns());
        wrapped.setHasAggregate(hasAggregate);
        wrapped.setHasWindow(hasWindow);
        wrapped.setAggregationType(aggregationType);
        return wrapped;
    }

    private static String toSqlLiteral(Object value) {
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "TRUE" : "FALSE";
        }
        String text = String.valueOf(value);
        if (text.startsWith("'") && text.endsWith("'")) {
            return text;
        }
        return "'" + text.replace("'", "''") + "'";
    }

    /**
     * 编译表达式字符串（默认使用 {@link FsscriptDialect#SQL_EXPRESSION} 方言，
     * 与历史行为一致：把函数式 {@code if(c, a, b)} 归一化为 {@code IIF(c, a, b)}）。
     * <p>
     * 使用共享的 Parser 实例编译表达式，可被其他类复用。
     * </p>
     *
     * @param expression 表达式字符串
     * @return 编译后的 AST
     * @throws RuntimeException 如果表达式语法错误
     */
    public static Exp compileExpression(String expression) {
        return compileExpression(expression, FsscriptDialect.SQL_EXPRESSION);
    }

    /**
     * 编译表达式字符串（指定方言）。
     * <p>
     * 方言负责在 parse 前对源码做必要的词法预处理。{@link FsscriptDialect#DEFAULT}
     * 不做预处理（保留 FSScript 完整保留字语义）；{@link FsscriptDialect#SQL_EXPRESSION}
     * 会把函数式 {@code if(...)} 改写为 {@code IIF(...)}。
     * </p>
     *
     * @param expression 表达式字符串
     * @param dialect    方言；传 null 等价于 {@link FsscriptDialect#DEFAULT}
     * @return 编译后的 AST
     * @throws RuntimeException 如果表达式语法错误
     * @since 8.1.11.beta
     */
    public static Exp compileExpression(String expression, FsscriptDialect dialect) {
        try {
            // 使用 compileEl 解析纯 fsscript 表达式
            // compile 是为 SQL 模板语法设计的（如 select ... where ${expr}），会把标识符当作字面量
            Exp exp = SHARED_PARSER.compileEl(null, expression, dialect);
            if (log.isDebugEnabled()) {
                String dialectName = (dialect == null ? "null" : dialect.getName());
                log.debug("Compiled expression '{}' (dialect={}) -> AST type: {}, AST: {}",
                        expression, dialectName, exp.getClass().getName(), exp);
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
    static SqlFragment evaluateExpression(Exp exp, SqlExpContext context, ApplicationContext appCtx) {
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

        throw RX.throwAUserTip("CALCULATED_FIELD_EXPRESSION_INVALID: calculatedFields.expression must return SqlFragment; got "
                + (result != null ? result.getClass().getName() : "null") + ".");
    }
}
