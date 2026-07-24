package com.foggyframework.dataset.model.engine.query_model;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.engine.compose.schema.AliasExtractor;
import com.foggyframework.dataset.model.engine.compose.schema.ColumnAliasParts;
import com.foggyframework.dataset.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 注入 QM 预定义计算字段。
 */
public final class PredefinedCalculatedFieldInjector {

    private PredefinedCalculatedFieldInjector() {
    }

    @SuppressWarnings("unchecked")
    public static void inject(DbQueryRequestDef queryRequest, QueryModel queryModel,
                              ModelResultContext context, Logger log) {
        if (!(queryModel instanceof QueryModelSupport)) {
            return;
        }
        QueryModelSupport qms = (QueryModelSupport) queryModel;
        List<CalculatedFieldDef> predefined = qms.getPredefinedCalculatedFields();
        if (predefined == null || predefined.isEmpty()) {
            return;
        }

        Set<String> predefinedNames = new HashSet<>();
        for (CalculatedFieldDef calc : predefined) {
            predefinedNames.add(calc.getName());
        }

        if (queryRequest.getCalculatedFields() != null) {
            // Request DTOs may be assembled with List.of/unmodifiableList by Java callers.
            // Injection owns its mutations, so never modify the caller-provided collection.
            List<CalculatedFieldDef> userFields = new ArrayList<>(queryRequest.getCalculatedFields());
            queryRequest.setCalculatedFields(userFields);
            List<String> replaced = new ArrayList<>();
            userFields.removeIf(f -> {
                if (f != null && predefinedNames.contains(f.getName()) && !isPredefinedInstance(f, predefined)) {
                    replaced.add(f.getName());
                    return true;
                }
                return false;
            });
            if (!replaced.isEmpty()) {
                String warning = "以下字段为预定义计算字段，已忽略您自定义的版本并使用模型预定义公式: " + replaced
                        + "。请直接在 columns 中引用，不要在 calculatedFields 中重复定义。";
                if (log != null) {
                    log.warn(warning);
                }
                if (context != null) {
                    List<String> engineWarnings = (List<String>) context.getExtData()
                            .computeIfAbsent("engineWarnings", k -> new ArrayList<>());
                    engineWarnings.add(warning);
                }
            }
        }

        Set<String> existingNames = new HashSet<>();
        if (queryRequest.getCalculatedFields() != null) {
            for (CalculatedFieldDef f : queryRequest.getCalculatedFields()) {
                existingNames.add(f.getName());
            }
        }

        Set<String> referencedColumns = collectReferences(queryRequest);

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
            existing.addAll(0, toInject);

            if (log != null && log.isDebugEnabled()) {
                log.debug("注入了 {} 个 QM 预定义计算字段: {}", toInject.size(),
                        toInject.stream().map(CalculatedFieldDef::getName).collect(Collectors.toList()));
            }
        }
    }

    private static Set<String> collectReferences(DbQueryRequestDef queryRequest) {
        Set<String> referencedColumns = new HashSet<>();
        collectColumnReferences(queryRequest.getColumns(), referencedColumns);
        collectConditionFields(queryRequest.getSlice(), referencedColumns);
        collectConditionFields(queryRequest.getHaving(), referencedColumns);
        collectConditionFields(queryRequest.getPostSlice(), referencedColumns);
        collectOrderFields(queryRequest.getOrderBy(), referencedColumns);
        collectGroupFields(queryRequest.getGroupBy(), referencedColumns);
        return referencedColumns;
    }

    private static boolean isPredefinedInstance(CalculatedFieldDef field, List<CalculatedFieldDef> predefined) {
        for (CalculatedFieldDef predefinedField : predefined) {
            if (field == predefinedField) {
                return true;
            }
        }
        return false;
    }

    private static void collectColumnReferences(List<String> columns, Set<String> out) {
        if (columns == null || columns.isEmpty()) {
            return;
        }
        for (String column : columns) {
            if (column == null || column.isBlank()) {
                continue;
            }
            out.add(column);
            String expression = column;
            try {
                ColumnAliasParts parts = AliasExtractor.extract(column);
                if (parts.hasAlias()) {
                    expression = parts.expression();
                    out.add(expression);
                }
            } catch (IllegalArgumentException ignore) {
                // Keep original field; downstream validation will report malformed aliases.
            }
            collectExpressionReferences(expression, out);
        }
    }

    private static void collectExpressionReferences(String expression, Set<String> out) {
        try {
            out.addAll(CalculatedFieldService.resolveBaseColumnReferences(expression, Collections.emptyMap()));
        } catch (Exception ignore) {
            // Not every column entry is a parseable expression; keep original references.
        }
    }

    private static void collectConditionFields(List<? extends CondRequestDef> conditions, Set<String> out) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        for (CondRequestDef item : conditions) {
            collectConditionField(item, out);
        }
    }

    private static void collectConditionField(CondRequestDef item, Set<String> out) {
        if (item == null) {
            return;
        }
        if (item.getField() != null && !item.getField().isBlank()) {
            out.add(item.getField());
        }
        if (item._isFieldReference()) {
            String referencedField = item._getReferencedField();
            if (referencedField != null && !referencedField.isBlank()) {
                out.add(referencedField);
            }
        }
        collectConditionFields(item.getAnd(), out);
        collectConditionFields(item.getOr(), out);
    }

    private static void collectOrderFields(List<OrderRequestDef> orderBy, Set<String> out) {
        if (orderBy == null || orderBy.isEmpty()) {
            return;
        }
        for (OrderRequestDef order : orderBy) {
            if (order != null && order.getField() != null && !order.getField().isBlank()) {
                out.add(order.getField());
            }
        }
    }

    private static void collectGroupFields(List<GroupRequestDef> groupBy, Set<String> out) {
        if (groupBy == null || groupBy.isEmpty()) {
            return;
        }
        for (GroupRequestDef group : groupBy) {
            if (group != null && group.getField() != null && !group.getField().isBlank()) {
                out.add(group.getField());
            }
        }
    }
}
