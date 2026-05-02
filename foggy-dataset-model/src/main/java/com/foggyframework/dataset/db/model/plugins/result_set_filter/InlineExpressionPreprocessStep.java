package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.compose.schema.AliasExtractor;
import com.foggyframework.dataset.db.model.engine.compose.schema.ColumnAliasParts;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.db.model.engine.expression.AllowedFunctions;
import com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.db.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpHolder;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlBinaryExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlFunctionExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlUnaryExp;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.fsscript.parser.spi.Exp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 内联表达式预处理步骤
 * <p>
 * 在所有其他 Step 之前执行，负责：
 * <ol>
 *   <li>解析 columns 中的内联表达式，转换为 CalculatedFieldDef</li>
 *   <li>识别所有列的聚合类型（内联表达式 AST 分析 + QueryModel 字段定义）</li>
 *   <li>将结果存入 ModelResultContext.parsedInlineExpressions</li>
 * </ol>
 * </p>
 *
 * <h3>聚合类型识别来源</h3>
 * <ol>
 *   <li>内联表达式 AST 分析（如 sum(amount) → SUM）</li>
 *   <li>内联表达式聚合推断（如 amount+2 在有其他聚合时 → SUM）</li>
 *   <li>预定义 calculatedFields 的 agg 属性</li>
 * </ol>
 * <p>
 * 注意：不再从 QueryModel 的 aggregation 属性读取默认聚合，
 * 聚合必须由用户显式指定（如使用 sum()、avg() 等函数）。
 * </p>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
@Slf4j
@Component
@Order(5)  // 在 AutoGroupByStep(10) 之前执行
public class InlineExpressionPreprocessStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        DbQueryRequestDef queryRequest = ctx.getRequest().getParam();
        List<String> columns = queryRequest.getColumns();

        if (columns == null || columns.isEmpty()) {
            return CONTINUE;
        }

        // 获取 QueryModel（用于查询字段定义）
        QueryModel queryModel = ctx.getQueryModel();

        // 注入 QM 预定义的 calculatedFields（同时处理 AI 误传的同名字段）
        injectPredefinedCalculatedFields(queryRequest, queryModel, ctx);

        // 解析并转换
        ModelResultContext.ParsedInlineExpressions result = parseAndConvert(columns, queryRequest, queryModel);

        // 存入 Context
        ctx.setParsedInlineExpressions(result);

        // 更新 queryRequest
        if (result.isProcessed()) {
            queryRequest.setColumns(result.getColumns());

            // 合并计算字段
            if (result.getCalculatedFields() != null && !result.getCalculatedFields().isEmpty()) {
                List<CalculatedFieldDef> existingFields = queryRequest.getCalculatedFields();
                if (existingFields == null) {
                    existingFields = new ArrayList<>();
                    queryRequest.setCalculatedFields(existingFields);
                }
                existingFields.addAll(result.getCalculatedFields());
            }
        }

        if (log.isDebugEnabled() && result.isProcessed()) {
            log.debug("InlineExpressionPreprocess: 解析了 {} 个内联表达式, 识别了 {} 个聚合列",
                    result.getAliasToExpression() != null ? result.getAliasToExpression().size() : 0,
                    result.getColumnAggregations() != null ? result.getColumnAggregations().size() : 0);
        }

        return CONTINUE;
    }

    /**
     * 解析 columns 并转换为预处理结果
     */
    private ModelResultContext.ParsedInlineExpressions parseAndConvert(
            List<String> columns,
            DbQueryRequestDef queryRequest,
            QueryModel queryModel) {

        ModelResultContext.ParsedInlineExpressions result = new ModelResultContext.ParsedInlineExpressions();
        result.setColumns(new ArrayList<>(columns.size()));
        result.setCalculatedFields(new ArrayList<>());
        result.setAliasToExpression(new LinkedHashMap<>());
        result.setColumnAggregations(new LinkedHashMap<>());

        int autoAliasCounter = 1;
        boolean hasAnyAggregate = false;

        // 第一遍：解析所有内联表达式，检测是否有聚合函数
        for (String columnDef : columns) {
            InlineExpressionParser.InlineExpression inlineExp = InlineExpressionParser.parse(columnDef);

            if (inlineExp != null) {
                // 这是一个内联表达式
                String alias = inlineExp.getAlias();
                if (alias == null) {
                    // 自动生成别名
                    alias = "expr_" + autoAliasCounter++;
                }

                // 创建 CalculatedFieldDef（origin=INLINE_EXPRESSION，便于下游 metadata / 日志按来源分流）
                CalculatedFieldDef calcFieldDef = new CalculatedFieldDef();
                calcFieldDef.setName(alias);
                calcFieldDef.setOrigin(CalculatedFieldDef.Origin.INLINE_EXPRESSION);

                // 通过 AST 分析检测聚合函数，填充 agg 字段，并存储编译后的 AST
                AggregateAnalysisResult aggResult = analyzeAggregateByAst(inlineExp.getExpression(), calcFieldDef);

                // 保持表达式原样
                calcFieldDef.setExpression(inlineExp.getExpression());
                if (aggResult.hasAggregate) {
                    calcFieldDef.setAgg(aggResult.aggregationType);
                    hasAnyAggregate = true;
                }

                result.getCalculatedFields().add(calcFieldDef);
                result.getAliasToExpression().put(alias, inlineExp);

                // 替换为别名
                result.getColumns().add(alias);

                if (log.isDebugEnabled()) {
                    log.debug("Inline expression converted: '{}' -> alias='{}', expression='{}', agg='{}', origin=INLINE_EXPRESSION, compiledExp={}",
                            columnDef, alias, calcFieldDef.getExpression(), calcFieldDef.getAgg(),
                            calcFieldDef.getCompiledExp() != null ? "已编译" : "未编译");
                }
            } else {
                // G5 v2-patch-2: F4 plain-field alias `"base AS alias"` → 合成 calc field
                // (origin=PLAIN_ALIAS)。命中 alias 时本分支接管；未命中 alias 时按原样保留。
                CalculatedFieldDef synthesized = trySynthesizePlainAlias(columnDef, queryRequest, queryModel, result);
                if (synthesized != null) {
                    result.getCalculatedFields().add(synthesized);
                    result.getColumns().add(synthesized.getName());
                } else {
                    result.getColumns().add(columnDef);
                }
            }
        }

        // 第二遍：如果存在聚合表达式，为没有聚合函数的内联表达式自动推断聚合类型
        if (hasAnyAggregate) {
            for (CalculatedFieldDef calcFieldDef : result.getCalculatedFields()) {
                if (calcFieldDef.getAgg() == null && calcFieldDef.getCompiledExp() != null) {
                    // 根据 AST 顶层节点类型推断聚合类型
                    String inferredAgg = inferAggregationFromAst(calcFieldDef.getCompiledExp());
                    if (inferredAgg != null) {
                        calcFieldDef.setAgg(inferredAgg);
                        if (log.isDebugEnabled()) {
                            log.debug("自动推断聚合类型: '{}' -> agg='{}'", calcFieldDef.getName(), inferredAgg);
                        }
                    }
                }
            }
        }

        // 第三遍：构建 columnAggregations 映射（统一聚合识别结果）
        // 1. 收集内联表达式的聚合类型
        for (CalculatedFieldDef calcFieldDef : result.getCalculatedFields()) {
            if (calcFieldDef.getAgg() != null) {
                result.getColumnAggregations().put(calcFieldDef.getName(), calcFieldDef.getAgg().toUpperCase());
            }
        }

        // 2. 收集预定义 calculatedFields 的聚合类型（非内联表达式）
        List<CalculatedFieldDef> existingCalcFields = queryRequest.getCalculatedFields();
        if (existingCalcFields != null) {
            for (CalculatedFieldDef calcFieldDef : existingCalcFields) {
                // 跳过已识别的（来自内联表达式）
                if (result.getColumnAggregations().containsKey(calcFieldDef.getName())) {
                    continue;
                }

                // 优先使用显式设置的 agg
                if (calcFieldDef.getAgg() != null) {
                    result.getColumnAggregations().put(calcFieldDef.getName(), calcFieldDef.getAgg().toUpperCase());
                    if (log.isDebugEnabled()) {
                        log.debug("从预定义 calculatedField 识别聚合列: '{}' -> agg='{}'",
                                calcFieldDef.getName(), calcFieldDef.getAgg());
                    }
                } else if (calcFieldDef.getExpression() != null) {
                    // 如果没有显式 agg，通过 AST 分析检测表达式中的聚合函数
                    AggregateAnalysisResult aggResult = analyzeAggregateByAst(calcFieldDef.getExpression(), calcFieldDef);
                    if (aggResult.hasAggregate && aggResult.aggregationType != null) {
                        calcFieldDef.setAgg(aggResult.aggregationType);
                        result.getColumnAggregations().put(calcFieldDef.getName(), aggResult.aggregationType);
                        hasAnyAggregate = true;
                        if (log.isDebugEnabled()) {
                            log.debug("通过 AST 分析识别预定义 calculatedField 聚合列: '{}' -> agg='{}'",
                                    calcFieldDef.getName(), aggResult.aggregationType);
                        }
                    }
                }
            }
        }

        // 3. [已移除] 不再从 QueryModel 读取默认 aggregation 属性
        // 聚合必须由用户在查询中显式指定（使用 sum()、avg() 等函数）
        // 这样可以避免隐式聚合行为，使查询逻辑更加明确

        // 4. 验证混合聚合查询：如果查询中包含聚合表达式，所有度量字段必须显式指定聚合函数
        if (hasAnyAggregate && queryModel != null) {
            validateMixedAggregation(result, queryModel);
        }

        return result;
    }

    /**
     * 验证混合聚合查询的合法性
     * <p>
     * 当查询中包含聚合表达式时，所有度量字段必须显式指定聚合函数。
     * 否则会导致语义不明确：度量字段应该如何处理（SUM/AVG/MAX/MIN）？
     * </p>
     *
     * @param result      解析结果
     * @param queryModel  查询模型
     * @throws IllegalArgumentException 如果检测到未聚合的度量字段
     */
    private void validateMixedAggregation(
            ModelResultContext.ParsedInlineExpressions result,
            QueryModel queryModel) {

        List<String> unaggregatedMeasures = new ArrayList<>();

        for (String columnName : result.getColumns()) {
            // 跳过已识别的聚合列
            if (result.getColumnAggregations().containsKey(columnName)) {
                continue;
            }

            // 检查是否是度量字段
            boolean isMeasure = false;
            for (TableModel tableModel : queryModel.getJdbcModelList()) {
                if (tableModel.findJdbcMeasureByName(columnName) != null) {
                    isMeasure = true;
                    break;
                }
            }

            if (isMeasure) {
                unaggregatedMeasures.add(columnName);
            }
        }

        // 如果存在未聚合的度量字段，抛出异常
        if (!unaggregatedMeasures.isEmpty()) {
            String measures = String.join("', '", unaggregatedMeasures);
            throw new IllegalArgumentException(
                    "检测到聚合查询中包含未聚合的度量字段: ['" + measures + "']。" +
                            "度量字段在聚合查询中必须显式指定聚合函数，例如: " +
                            "sum(" + unaggregatedMeasures.get(0) + "), " +
                            "avg(" + unaggregatedMeasures.get(0) + "), " +
                            "max(" + unaggregatedMeasures.get(0) + "), " +
                            "min(" + unaggregatedMeasures.get(0) + ") 等。"
            );
        }
    }

    /**
     * 根据 AST 顶层节点类型推断聚合类型
     * <p>
     * 推断规则：
     * <ul>
     *     <li>四则运算（+、-、*、/） → SUM</li>
     *     <li>字符串函数 → null（无聚合，需加入 groupBy）</li>
     *     <li>其他情况 → null</li>
     * </ul>
     * </p>
     *
     * @param exp AST 顶层节点
     * @return 推断的聚合类型，null 表示无法推断或不需要聚合
     */
    private String inferAggregationFromAst(Exp exp) {
        if (exp == null) {
            return null;
        }

        // 解包 SqlExpHolder
        Exp innerExp = exp;
        if (exp instanceof SqlExpHolder) {
            innerExp = ((SqlExpHolder) exp).getInnerSqlExp();
        }

        // 顶层是四则运算 → 默认 SUM
        if (innerExp instanceof SqlBinaryExp) {
            SqlBinaryExp binaryExp = (SqlBinaryExp) innerExp;
            String op = binaryExp.getOperator();
            if ("+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op)) {
                return "SUM";
            }
        }

        // 顶层是函数调用
        if (innerExp instanceof SqlFunctionExp) {
            SqlFunctionExp funcExp = (SqlFunctionExp) innerExp;
            String funcName = funcExp.getFunctionName().toUpperCase();

            // 字符串函数 → 无聚合
            if (AllowedFunctions.STRING_FUNCTIONS.contains(funcName)) {
                return null;
            }

            // 数学函数 → SUM（因为结果是数值）
            if (AllowedFunctions.MATH_FUNCTIONS.contains(funcName)) {
                return "SUM";
            }
        }

        return null;
    }

    /**
     * 通过 AST 分析检测表达式中的聚合函数
     * <p>
     * 比正则更精确，可以检测到嵌套的聚合函数，如 "sum(a) + count(*)"。
     * 同时将编译后的 AST 存储到 calcFieldDef.compiledExp 中供后续复用。
     * </p>
     *
     * @param expression   表达式字符串
     * @param calcFieldDef 计算字段定义，用于存储编译后的 AST
     * @return 聚合分析结果
     */
    private AggregateAnalysisResult analyzeAggregateByAst(String expression, CalculatedFieldDef calcFieldDef) {
        if (expression == null || expression.isEmpty()) {
            return AggregateAnalysisResult.NONE;
        }

        try {
            // 使用 CalculatedFieldService 的共享解析器编译表达式
            Exp exp = CalculatedFieldService.compileExpression(expression);

            // 存储编译后的 AST 供后续 CalculatedFieldService 复用
            calcFieldDef.setCompiledExp(exp);

            // 遍历 AST 检测聚合函数
            AggregateVisitor visitor = new AggregateVisitor();
            visitExp(exp, visitor);

            return visitor.toResult();
        } catch (Exception e) {
            // 解析失败时回退到简单检测（不存储 AST，后续会重新编译）
            if (log.isDebugEnabled()) {
                log.debug("AST 分析失败，回退到简单检测: {}", e.getMessage());
            }
            return fallbackAggregateDetection(expression);
        }
    }

    /**
     * 遍历 AST 节点检测聚合函数
     * <p>
     * 处理各种 AST 类型：
     * <ul>
     *     <li>SqlFunctionExp - 直接的函数表达式</li>
     *     <li>SqlBinaryExp - 二元运算表达式</li>
     *     <li>SqlUnaryExp - 一元运算表达式</li>
     *     <li>SqlExpHolder - 包装类（SqlExpWrapper、SqlExpFunCallWrapper）</li>
     * </ul>
     * </p>
     */
    private void visitExp(Exp exp, AggregateVisitor visitor) {
        if (exp == null) {
            return;
        }

        // 检查是否是 SqlFunctionExp
        if (exp instanceof SqlFunctionExp) {
            SqlFunctionExp funcExp = (SqlFunctionExp) exp;
            String funcName = funcExp.getFunctionName().toUpperCase();

            if (AllowedFunctions.isAggregateFunction(funcName)) {
                visitor.hasAggregate = true;
                visitor.aggregateCount++;
                if (visitor.firstAggregateType == null) {
                    visitor.firstAggregateType = funcName;
                }
            }

            // 递归检查参数
            if (funcExp.getArgs() != null) {
                for (Exp arg : funcExp.getArgs()) {
                    visitExp(arg, visitor);
                }
            }
            return;
        }

        // 检查 SqlBinaryExp
        if (exp instanceof SqlBinaryExp) {
            SqlBinaryExp binaryExp = (SqlBinaryExp) exp;
            visitExp(binaryExp.getLeft(), visitor);
            visitExp(binaryExp.getRight(), visitor);
            return;
        }

        // 检查 SqlUnaryExp
        if (exp instanceof SqlUnaryExp) {
            SqlUnaryExp unaryExp = (SqlUnaryExp) exp;
            visitExp(unaryExp.getOperand(), visitor);
            return;
        }

        // 检查 SqlExpHolder (SqlExpWrapper, SqlExpFunCallWrapper)
        if (exp instanceof SqlExpHolder) {
            visitExp(((SqlExpHolder) exp).getInnerSqlExp(), visitor);
        }
    }

    /**
     * 简单的聚合函数检测（回退方案）
     */
    private AggregateAnalysisResult fallbackAggregateDetection(String expression) {
        String upper = expression.toUpperCase();
        String[] aggregates = {"SUM", "AVG", "COUNT", "COUNTD", "COUNT_DISTINCT", "MAX", "MIN",
                "STDDEV_POP", "STDDEV_SAMP", "VAR_POP", "VAR_SAMP"};

        String foundType = null;
        int count = 0;

        for (String agg : aggregates) {
            if (upper.contains(agg + "(")) {
                count++;
                if (foundType == null) {
                    foundType = agg;
                }
            }
        }

        if (count == 0) {
            return AggregateAnalysisResult.NONE;
        }

        return new AggregateAnalysisResult(true, count == 1 ? foundType : null);
    }

    /**
     * 聚合函数访问器（用于 AST 遍历时收集聚合信息）
     */
    private static class AggregateVisitor {
        boolean hasAggregate;
        int aggregateCount;
        String firstAggregateType;

        /**
         * 转换为分析结果
         * @return 聚合分析结果，如果有多个聚合函数则 aggregationType 为 null
         */
        AggregateAnalysisResult toResult() {
            if (!hasAggregate) {
                return AggregateAnalysisResult.NONE;
            }
            return new AggregateAnalysisResult(
                    true,
                    aggregateCount == 1 ? firstAggregateType : null
            );
        }
    }

    /**
     * 聚合分析结果（不可变）
     */
    private static class AggregateAnalysisResult {
        static final AggregateAnalysisResult NONE = new AggregateAnalysisResult(false, null);

        final boolean hasAggregate;
        final String aggregationType;

        AggregateAnalysisResult(boolean hasAggregate, String aggregationType) {
            this.hasAggregate = hasAggregate;
            this.aggregationType = aggregationType;
        }
    }

    /**
     * 注入 QM 预定义的 calculatedFields
     * <p>
     * 仅注入查询 columns 中引用到的预定义字段。
     * DSL 请求中同名的 calculatedField 可覆盖 QM 预定义的。
     * </p>
     */
    /**
     * 注入 QM 预定义计算字段，并处理与用户自定义字段的同名冲突。
     *
     * <p>安全策略：仅移除与 QM 预定义计算字段同名的用户自定义字段（AI 常见误用）。
     * 与普通列（维度/度量/属性）同名的冲突由 CalculatedFieldService 拦截，
     * 防止用户通过自定义计算字段覆盖已有列（可能涉及权限控制等安全敏感字段）。</p>
     */
    @SuppressWarnings("unchecked")
    private void injectPredefinedCalculatedFields(DbQueryRequestDef queryRequest, QueryModel queryModel,
                                                   ModelResultContext ctx) {
        if (!(queryModel instanceof QueryModelSupport)) {
            return;
        }
        QueryModelSupport qms = (QueryModelSupport) queryModel;
        List<CalculatedFieldDef> predefined = qms.getPredefinedCalculatedFields();
        if (predefined == null || predefined.isEmpty()) {
            return;
        }

        // 建立预定义字段名称索引
        Set<String> predefinedNames = new HashSet<>();
        for (CalculatedFieldDef calc : predefined) {
            predefinedNames.add(calc.getName());
        }

        // 移除与预定义字段同名的用户自定义字段，并记录 warning
        if (queryRequest.getCalculatedFields() != null) {
            List<CalculatedFieldDef> userFields = queryRequest.getCalculatedFields();
            List<String> replaced = new ArrayList<>();
            userFields.removeIf(f -> {
                if (predefinedNames.contains(f.getName())) {
                    replaced.add(f.getName());
                    return true;
                }
                return false;
            });
            if (!replaced.isEmpty()) {
                String warning = "以下字段为预定义计算字段，已忽略您自定义的版本并使用模型预定义公式: " + replaced
                        + "。请直接在 columns 中引用，不要在 calculatedFields 中重复定义。";
                log.warn(warning);
                // 写入 extData，由 SemanticQueryServiceV3Impl 收集到 response.warnings
                if (ctx != null) {
                    List<String> engineWarnings = (List<String>) ctx.getExtData()
                            .computeIfAbsent("engineWarnings", k -> new ArrayList<>());
                    engineWarnings.add(warning);
                }
            }
        }

        // 收集 DSL 请求中剩余的 calculatedField 名称
        Set<String> existingNames = new HashSet<>();
        if (queryRequest.getCalculatedFields() != null) {
            for (CalculatedFieldDef f : queryRequest.getCalculatedFields()) {
                existingNames.add(f.getName());
            }
        }

        // 收集 columns 中引用到的名称
        Set<String> referencedColumns = new HashSet<>();
        if (queryRequest.getColumns() != null) {
            referencedColumns.addAll(queryRequest.getColumns());
        }

        // 注入引用到的、且未被 DSL 覆盖的预定义字段
        List<CalculatedFieldDef> toInject = new ArrayList<>();
        for (CalculatedFieldDef calc : predefined) {
            if (referencedColumns.contains(calc.getName()) && !existingNames.contains(calc.getName())) {
                toInject.add(calc);
            }
        }

        if (!toInject.isEmpty()) {
            List<CalculatedFieldDef> existing = queryRequest.getCalculatedFields();
            if (existing == null) {
                existing = new ArrayList<>();
                queryRequest.setCalculatedFields(existing);
            }
            // 预定义字段放在前面，DSL 请求中的放在后面
            existing.addAll(0, toInject);

            if (log.isDebugEnabled()) {
                log.debug("注入了 {} 个 QM 预定义计算字段: {}", toInject.size(),
                        toInject.stream().map(CalculatedFieldDef::getName).collect(java.util.stream.Collectors.toList()));
            }
        }
    }

    /**
     * G5 v2-patch-2 · F4 plain-field alias-only 合成 calc field（Option A）。
     *
     * <p>当 {@link InlineExpressionParser#parse(String)} 返回 null（即非函数表达式）
     * 时本方法接管，识别 {@code "base AS alias"} 形态并合成
     * {@code CalculatedFieldDef(name=alias, expression=base, origin=PLAIN_ALIAS)}。
     * 返回 null 表示当前列不是 plain-alias，调用方应保持列原样。
     *
     * <h3>命名冲突 fail-fast</h3>
     * <ul>
     *   <li>{@code COLUMN_ALIAS_DUPLICATE} — 同请求多列 alias 重复</li>
     *   <li>{@code COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD} — alias 命中 QM 预定义或 request.calculatedFields 同名 calc field</li>
     *   <li>{@code COLUMN_ALIAS_COLLIDES_WITH_PHYSICAL_FIELD} — alias 命中 QM 物理字段名</li>
     *   <li>{@code COLUMN_FIELD_NOT_FOUND} — base 字段在 QM 不存在（错误信息以 alias 视角输出）</li>
     * </ul>
     *
     * <h3>元数据继承</h3>
     * <p>合成时主动从 base 列拷贝 {@code caption} / {@code description}，避免
     * alias 后字段语义降级（type / formatter 由 SqlFragment 推断链路自动继承）。
     *
     * @param columnDef     原始列字符串
     * @param queryRequest  当前请求（含 QM 预定义 + 用户 calc fields）
     * @param queryModel    当前 QM
     * @param result        累计的 ParsedInlineExpressions（含本批次已合成的 calc）
     * @return 合成的 CalculatedFieldDef；列不是 plain-alias 时返回 null
     * @throws IllegalArgumentException 命名冲突或字段不存在时抛出，消息以 {@code COLUMN_*} 短码起头
     */
    private CalculatedFieldDef trySynthesizePlainAlias(
            String columnDef,
            DbQueryRequestDef queryRequest,
            QueryModel queryModel,
            ModelResultContext.ParsedInlineExpressions result) {

        if (columnDef == null || columnDef.indexOf(' ') < 0) {
            // 无空格 → 不可能含 AS → 不可能是 plain-alias
            return null;
        }

        ColumnAliasParts parts;
        try {
            parts = AliasExtractor.extract(columnDef);
        } catch (IllegalArgumentException ex) {
            // 解析失败（空白等异常输入）：不合成，交回原路径暴露原错误
            return null;
        }
        if (!parts.hasAlias()) {
            return null;
        }

        String baseField = parts.expression();
        String aliasName = parts.outputName();

        // base 必须是简单 identifier 或 dim$attr 引用；否则不合成，回退原路径
        if (!isSimpleFieldOrDimAttrRef(baseField)) {
            return null;
        }

        // C3：同请求 alias 重复（含本批次已合成的）
        for (CalculatedFieldDef existing : result.getCalculatedFields()) {
            if (aliasName.equals(existing.getName())) {
                throw new IllegalArgumentException(
                        "COLUMN_ALIAS_DUPLICATE: column '" + columnDef
                                + "' alias '" + aliasName
                                + "' is already used by another column in this request");
            }
        }

        // C1：alias 命中已有 calc field（QM 预定义 + request 用户声明）
        List<CalculatedFieldDef> existingCalcFields = queryRequest.getCalculatedFields();
        if (existingCalcFields != null) {
            for (CalculatedFieldDef existing : existingCalcFields) {
                if (aliasName.equals(existing.getName())) {
                    throw new IllegalArgumentException(
                            "COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD: column '" + columnDef
                                    + "' alias '" + aliasName
                                    + "' collides with an existing calculated field "
                                    + "(declared in QueryModel or in request.calculatedFields). "
                                    + "Use a different alias.");
                }
            }
        }
        // C1-ext：alias 命中 QM 预定义 calc field（可能未注入到 request.calculatedFields）
        if (queryModel instanceof QueryModelSupport) {
            List<CalculatedFieldDef> predefined = ((QueryModelSupport) queryModel).getPredefinedCalculatedFields();
            if (predefined != null) {
                for (CalculatedFieldDef pd : predefined) {
                    if (aliasName.equals(pd.getName())) {
                        throw new IllegalArgumentException(
                                "COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD: column '" + columnDef
                                        + "' alias '" + aliasName
                                        + "' collides with a predefined calculated field in QueryModel. "
                                        + "Use a different alias.");
                    }
                }
            }
        }

        // C2：alias 命中 QM 物理字段（防止静默 shadow）+ base 存在性校验 + 元数据探测
        DbQueryColumn baseColumn = null;
        if (queryModel instanceof QueryModelSupport) {
            QueryModelSupport qms = (QueryModelSupport) queryModel;

            DbQueryColumn aliasCollision = qms.findJdbcColumnForSelectByName(aliasName, false);
            if (aliasCollision != null) {
                // C2-refinement: 如果冲突列实际是公式度量（TM 级 formulaDef），
                // 应报 COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD 而非 PHYSICAL_FIELD，
                // 因为公式度量从用户视角是 "计算字段"。
                boolean isFormulaMeasure = false;
                for (TableModel tm : qms.getJdbcModelList()) {
                    com.foggyframework.dataset.db.model.spi.DbMeasure measure = tm.findJdbcMeasureByName(aliasName);
                    if (measure != null) {
                        com.foggyframework.dataset.db.model.impl.measure.DbMeasureSupport ms =
                                measure.getDecorate(com.foggyframework.dataset.db.model.impl.measure.DbMeasureSupport.class);
                        if (ms != null && ms.getFormulaBuilder() != null) {
                            isFormulaMeasure = true;
                        }
                        break;
                    }
                }
                if (isFormulaMeasure) {
                    throw new IllegalArgumentException(
                            "COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD: column '" + columnDef
                                    + "' alias '" + aliasName
                                    + "' collides with a formula-derived field in the QueryModel. "
                                    + "Use a different alias.");
                }
                throw new IllegalArgumentException(
                        "COLUMN_ALIAS_COLLIDES_WITH_PHYSICAL_FIELD: column '" + columnDef
                                + "' alias '" + aliasName
                                + "' collides with a physical field declared in the QueryModel. "
                                + "Use a different alias to avoid silent shadowing.");
            }


            // 先检查 base 是否是本批次已合成的 calc field（支持链式 alias，如 a→x→y）
            boolean baseIsSynthesized = false;
            for (CalculatedFieldDef synth : result.getCalculatedFields()) {
                if (baseField.equals(synth.getName())) {
                    baseIsSynthesized = true;
                    break;
                }
            }

            if (!baseIsSynthesized) {
                baseColumn = qms.findJdbcColumnForSelectByName(baseField, false);
                if (baseColumn == null) {
                    // 8.4.0.beta backlog B-03 FU-2 · 当 baseField 实际命中模型上
                    // 的 DbDimension 时，给出"维度不可直接投影 → $caption"提示，
                    // 让 LLM/用户的 copy-paste 修复保留用户 alias。其他情况保留
                    // 原有 generic 错误消息。
                    DbDimension dim = null;
                    try {
                        dim = qms.findDimension(baseField);
                    } catch (Exception ignore) {
                        // findDimension may throw on rare model shapes; treat
                        // as "not a dim" and fall back to the generic message.
                    }
                    if (dim != null) {
                        String hintCaption = baseField + "$caption";
                        String hintId = baseField + "$id";
                        throw new IllegalArgumentException(
                                "COLUMN_FIELD_NOT_FOUND: column '" + columnDef
                                        + "' references dimension '" + baseField
                                        + "' directly. Dimensions are not projectable; "
                                        + "reference an attribute (e.g. '" + hintCaption
                                        + "' or '" + hintId + "'). "
                                        + "Hint: did you mean '" + hintCaption + " AS "
                                        + aliasName + "'?");
                    }
                    throw new IllegalArgumentException(
                            "COLUMN_FIELD_NOT_FOUND: field '" + baseField
                                    + "' (referenced by alias '" + aliasName
                                    + "') not found in QueryModel");
                }
            }
        }

        // 合成 CalculatedFieldDef（origin=PLAIN_ALIAS）
        CalculatedFieldDef synth = new CalculatedFieldDef();
        synth.setName(aliasName);
        synth.setExpression(baseField);
        synth.setOrigin(CalculatedFieldDef.Origin.PLAIN_ALIAS);

        // 元数据继承（spec §3.1.2）：从 base 列拷贝 caption / description；
        // type / formatter 由 SqlFragment.inferColumnType 推断链路自动继承，无需显式拷贝。
        if (baseColumn != null) {
            if (StringUtils.isNotEmpty(baseColumn.getCaption())) {
                synth.setCaption(baseColumn.getCaption());
            }
            if (StringUtils.isNotEmpty(baseColumn.getDescription())) {
                synth.setDescription(baseColumn.getDescription());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Plain field alias rewritten: '{}' -> alias='{}', base='{}', origin=PLAIN_ALIAS",
                    columnDef, aliasName, baseField);
        }

        return synth;
    }

    /**
     * 判断是否为"简单字段引用"或"维度属性引用"。
     * <p>
     * 排除：嵌套维度（含 {@code .}）、多级 {@code $}、函数 / 操作符 / 空格等。
     * {@code "name"} / {@code "customerId"} / {@code "_internal"} 通过；
     * {@code "product$id"} / {@code "product$caption"} 通过；
     * {@code "product.name"} / {@code "product$caption$zh"} / {@code "SUM(x)"} 不通过。
     */
    private static boolean isSimpleFieldOrDimAttrRef(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        int dollar = s.indexOf('$');
        if (dollar < 0) {
            return isSimpleIdentifier(s);
        }
        if (dollar == 0 || dollar == s.length() - 1 || dollar != s.lastIndexOf('$')) {
            return false;
        }
        return isSimpleIdentifier(s.substring(0, dollar))
                && isSimpleIdentifier(s.substring(dollar + 1));
    }

    private static boolean isSimpleIdentifier(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        char first = s.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                continue;
            }
            return false;
        }
        return true;
    }
}
