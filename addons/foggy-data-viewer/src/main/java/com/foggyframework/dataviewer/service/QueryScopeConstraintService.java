package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.config.DataViewerProperties.ModelScopeConstraint;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 查询范围约束服务
 * <p>
 * 作为第二层安全保障，即使AI提供了过滤条件，
 * 也要验证查询范围是否在允许的范围内。
 * 使用类型安全的 SliceRequestDef。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryScopeConstraintService {

    private final DataViewerProperties properties;

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    /**
     * 验证并强制执行范围约束
     *
     * @param model 查询模型名称
     * @param slice 过滤条件
     * @return 验证/调整后的过滤条件
     * @throws IllegalArgumentException 如果没有提供有效的范围过滤条件
     */
    public List<SliceRequestDef> enforceConstraints(String model, List<SliceRequestDef> slice) {
        if (!properties.getScopeConstraints().isEnabled()) {
            return slice;
        }

        ModelScopeConstraint constraint = properties.getScopeConstraints().getModels().get(model);

        if (constraint == null) {
            // 没有特定模型的约束，只验证slice不为空
            if (slice == null || slice.isEmpty()) {
                throw new IllegalArgumentException(
                        "At least one filter condition is required to limit query scope"
                );
            }
            return slice;
        }

        // 确保slice是可变的
        List<SliceRequestDef> mutableSlice = new ArrayList<>(slice != null ? slice : List.of());

        // 查找范围字段过滤条件
        String scopeField = constraint.getScopeField();
        Optional<SliceRequestDef> scopeFilter = findScopeFilter(mutableSlice, scopeField);

        if (scopeFilter.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Query must include a filter on '%s' to limit scope. " +
                            "For example: {\"field\": \"%s\", \"op\": \">=\", \"value\": \"2025-01-01\"}",
                    scopeField, scopeField
            ));
        }

        // 验证时间范围
        int maxDays = constraint.getMaxDurationDays();
        validateAndAdjustDuration(mutableSlice, scopeField, maxDays);

        return mutableSlice;
    }

    /**
     * 查找指定字段的过滤条件
     */
    private Optional<SliceRequestDef> findScopeFilter(List<SliceRequestDef> slice, String scopeField) {
        return slice.stream()
                .filter(item -> scopeField.equals(item.getField()))
                .findFirst();
    }

    /**
     * 验证并调整时间范围
     */
    private void validateAndAdjustDuration(List<SliceRequestDef> slice, String scopeField, int maxDays) {
        LocalDate startDate = extractStartDate(slice, scopeField);
        LocalDate endDate = extractEndDate(slice, scopeField);

        if (startDate != null && endDate == null) {
            // 只有开始日期，自动添加结束日期约束
            LocalDate autoEndDate = startDate.plusDays(maxDays);
            SliceRequestDef endFilter = new SliceRequestDef(scopeField, "<", autoEndDate.toString());
            slice.add(endFilter);
            log.info("Auto-added end date constraint: {} < {}", scopeField, autoEndDate);
        } else if (startDate != null && endDate != null) {
            // 验证范围不超过最大值
            long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > maxDays) {
                throw new IllegalArgumentException(String.format(
                        "Query range exceeds maximum allowed duration of %d days. " +
                                "Please narrow your date range.", maxDays
                ));
            }
        }
    }

    /**
     * 提取开始日期
     */
    private LocalDate extractStartDate(List<SliceRequestDef> slice, String scopeField) {
        return slice.stream()
                .filter(item -> scopeField.equals(item.getField()))
                .filter(item -> {
                    String op = item.getOp();
                    return ">=".equals(op) || ">".equals(op);
                })
                .map(item -> parseDate(item.getValue()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 提取结束日期
     */
    private LocalDate extractEndDate(List<SliceRequestDef> slice, String scopeField) {
        return slice.stream()
                .filter(item -> scopeField.equals(item.getField()))
                .filter(item -> {
                    String op = item.getOp();
                    return "<=".equals(op) || "<".equals(op);
                })
                .map(item -> parseDate(item.getValue()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析日期
     */
    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        String dateStr = String.valueOf(value);
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        log.warn("Could not parse date: {}", dateStr);
        return null;
    }
}
