package com.foggyframework.dataset.jdbc.model.plugins.result_set_filter;

import com.foggyframework.dataset.jdbc.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.expression.AllowedFunctions;
import com.foggyframework.dataset.jdbc.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.jdbc.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.jdbc.model.engine.expression.sql.SqlBinaryExp;
import com.foggyframework.dataset.jdbc.model.engine.expression.sql.SqlFunctionExp;
import com.foggyframework.dataset.jdbc.model.engine.expression.sql.SqlUnaryExp;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.exp.UnresolvedFunCall;
import com.foggyframework.fsscript.parser.spi.Exp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内联表达式预处理步骤
 * <p>
 * 在所有其他 Step 之前执行，解析 columns 中的内联表达式，
 * 转换为 CalculatedFieldDef，避免后续重复解析。
 * </p>
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>遍历 columns，使用 InlineExpressionParser 解析每个列定义</li>
 *   <li>将内联表达式转换为 CalculatedFieldDef</li>
 *   <li>通过 AST 分析识别聚合函数，填充 agg 字段</li>
 *   <li>将结果存入 ModelResultContext.parsedInlineExpressions</li>
 *   <li>更新 queryRequest.columns 和 queryRequest.calculatedFields</li>
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
        JdbcQueryRequestDef queryRequest = ctx.getRequest().getParam();
        List<String> columns = queryRequest.getColumns();

        if (columns == null || columns.isEmpty()) {
            return CONTINUE;
        }

        // 解析并转换
        ModelResultContext.ParsedInlineExpressions result = parseAndConvert(columns, queryRequest);

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
            log.debug("InlineExpressionPreprocess: 解析了 {} 个内联表达式",
                    result.getAliasToExpression() != null ? result.getAliasToExpression().size() : 0);
        }

        return CONTINUE;
    }

    /**
     * 解析 columns 并转换为预处理结果
     */
    private ModelResultContext.ParsedInlineExpressions parseAndConvert(
            List<String> columns,
            JdbcQueryRequestDef queryRequest) {

        ModelResultContext.ParsedInlineExpressions result = new ModelResultContext.ParsedInlineExpressions();
        result.setColumns(new ArrayList<>(columns.size()));
        result.setCalculatedFields(new ArrayList<>());
        result.setAliasToExpression(new LinkedHashMap<>());

        int autoAliasCounter = 1;

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
                calcFieldDef.setExpression(inlineExp.getExpression());

                // 通过 AST 分析检测聚合函数，填充 agg 字段，并存储编译后的 AST
                AggregateAnalysisResult aggResult = analyzeAggregateByAst(inlineExp.getExpression(), calcFieldDef);
                if (aggResult.hasAggregate) {
                    calcFieldDef.setAgg(aggResult.aggregationType);
                }

                result.getCalculatedFields().add(calcFieldDef);
                result.getAliasToExpression().put(alias, inlineExp);

                // 替换为别名
                result.getColumns().add(alias);

                if (log.isDebugEnabled()) {
                    log.debug("解析内联表达式: '{}' -> alias='{}', expression='{}', agg='{}', compiledExp={}",
                            columnDef, alias, inlineExp.getExpression(), aggResult.aggregationType,
                            calcFieldDef.getCompiledExp() != null ? "已编译" : "未编译");
                }
            } else {
                // 保持原样
                result.getColumns().add(columnDef);
            }
        }

        return result;
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

            return new AggregateAnalysisResult(
                    visitor.hasAggregate,
                    visitor.aggregateCount == 1 ? visitor.firstAggregateType : null
            );
        } catch (Exception e) {
            // 解析失败时回退到简单检测（不存储 AST，后续会重新编译）
            if (log.isDebugEnabled()) {
                log.debug("AST 分析失败，回退到简单检测: {}", e.getMessage());
            }
            return fallbackAggregateDetection(expression);
        }
    }

    /**
     * 遍历 AST 节点
     * <p>
     * 处理各种 AST 类型：
     * <ul>
     *     <li>SqlFunctionExp - 直接的函数表达式</li>
     *     <li>SqlBinaryExp - 二元运算表达式</li>
     *     <li>SqlUnaryExp - 一元运算表达式</li>
     *     <li>SqlExpWrapper (UnresolvedFunCall) - 包装的函数调用</li>
     *     <li>SqlExpFunCallWrapper (AbstractExp) - 包装的函数表达式</li>
     * </ul>
     * </p>
     */
    private void visitExp(Exp exp, AggregateVisitor visitor) {
        if (exp == null) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("visitExp: type={}", exp.getClass().getName());
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

        // 检查 SqlExpWrapper (UnresolvedFunCall 的子类)
        // 通过反射获取内部的 sqlExp 字段
        if (exp instanceof UnresolvedFunCall) {
            Exp innerExp = extractInnerExp(exp, "sqlExp");
            if (innerExp != null) {
                visitExp(innerExp, visitor);
            }
            return;
        }

        // 检查 SqlExpFunCallWrapper (AbstractExp 的子类)
        // 通过 value 字段获取内部表达式
        if (exp instanceof AbstractExp) {
            Object value = ((AbstractExp<?>) exp).getValue();
            if (value instanceof Exp) {
                visitExp((Exp) value, visitor);
            }
            return;
        }
    }

    /**
     * 通过反射提取内部表达式
     */
    private Exp extractInnerExp(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            if (value instanceof Exp) {
                return (Exp) value;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // 字段不存在或无法访问，忽略
            if (log.isDebugEnabled()) {
                log.debug("Cannot extract field '{}' from {}: {}", fieldName, obj.getClass().getName(), e.getMessage());
            }
        }
        return null;
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
     * 聚合函数访问器
     */
    private static class AggregateVisitor {
        boolean hasAggregate = false;
        int aggregateCount = 0;
        String firstAggregateType = null;
    }

    /**
     * 聚合分析结果
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
