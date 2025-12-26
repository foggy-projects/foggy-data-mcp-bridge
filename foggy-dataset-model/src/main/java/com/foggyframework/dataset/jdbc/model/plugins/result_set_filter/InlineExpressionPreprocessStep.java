package com.foggyframework.dataset.jdbc.model.plugins.result_set_filter;

import com.foggyframework.dataset.jdbc.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.jdbc.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.expression.AllowedFunctions;
import com.foggyframework.dataset.jdbc.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.jdbc.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.jdbc.model.engine.expression.SqlExpHolder;
import com.foggyframework.dataset.jdbc.model.engine.expression.sql.SqlBinaryExp;
import com.foggyframework.dataset.jdbc.model.engine.expression.sql.SqlFunctionExp;
import com.foggyframework.dataset.jdbc.model.engine.expression.sql.SqlUnaryExp;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.fsscript.parser.spi.Exp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

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
 *   <li>内联表达式 AST 分析（如 sum(totalAmount) → SUM）</li>
 *   <li>内联表达式聚合推断（如 totalAmount+2 在有其他聚合时 → SUM）</li>
 *   <li>QueryModel 字段定义（如 formulaDef 字段的 aggregation）</li>
 * </ol>
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
        QueryModel queryModel = ctx.getJdbcQueryModel();

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

                // 创建 CalculatedFieldDef
                CalculatedFieldDef calcFieldDef = new CalculatedFieldDef();
                calcFieldDef.setName(alias);

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
                    log.debug("解析内联表达式: '{}' -> alias='{}', expression='{}', agg='{}', compiledExp={}",
                            columnDef, alias, calcFieldDef.getExpression(), calcFieldDef.getAgg(),
                            calcFieldDef.getCompiledExp() != null ? "已编译" : "未编译");
                }
            } else {
                // 保持原样
                result.getColumns().add(columnDef);
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
                if (calcFieldDef.getAgg() != null) {
                    result.getColumnAggregations().put(calcFieldDef.getName(), calcFieldDef.getAgg().toUpperCase());
                    if (log.isDebugEnabled()) {
                        log.debug("从预定义 calculatedField 识别聚合列: '{}' -> agg='{}'",
                                calcFieldDef.getName(), calcFieldDef.getAgg());
                    }
                }
            }
        }

        // 3. 检查普通列（非内联表达式）在 QueryModel 中的聚合定义
        if (queryModel != null) {
            for (String columnName : result.getColumns()) {
                // 跳过已识别的聚合列（来自内联表达式）
                if (result.getColumnAggregations().containsKey(columnName)) {
                    continue;
                }

                // 从 QueryModel 获取字段聚合类型
                DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName(columnName, false);
                if (queryColumn != null) {
                    DbColumn selectColumn = queryColumn.getSelectColumn();
                    if (selectColumn != null) {
                        DbAggregation agg = selectColumn.getAggregation();
                        if (agg != null) {
                            result.getColumnAggregations().put(columnName, agg.name());
                            // 如果之前没检测到聚合，但 QueryModel 有聚合定义，更新标记
                            hasAnyAggregate = true;
                            if (log.isDebugEnabled()) {
                                log.debug("从 QueryModel 识别聚合列: '{}' -> agg='{}'", columnName, agg.name());
                            }
                        }
                    }
                }
            }
        }

        return result;
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
        String[] aggregates = {"SUM", "AVG", "COUNT", "MAX", "MIN"};

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
}
