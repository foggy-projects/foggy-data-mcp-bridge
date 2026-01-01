package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.config.DataViewerProperties.ModelScopeConstraint;
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
 * 也要验证查询范围是否在允许的范围内
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
    public List<Map<String, Object>> enforceConstraints(String model, List<Map<String, Object>> slice) {
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
        List<Map<String, Object>> mutableSlice = new ArrayList<>(slice != null ? slice : List.of());

        // 查找范围字段过滤条件
        String scopeField = constraint.getScopeField();
        Optional<Map<String, Object>> scopeFilter = findScopeFilter(mutableSlice, scopeField);

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
    private Optional<Map<String, Object>> findScopeFilter(List<Map<String, Object>> slice, String scopeField) {
        return slice.stream()
                .filter(item -> scopeField.equals(item.get("field")))
                .findFirst();
    }

    /**
     * 验证并调整时间范围
     */
    private void validateAndAdjustDuration(List<Map<String, Object>> slice, String scopeField, int maxDays) {
        LocalDate startDate = extractStartDate(slice, scopeField);
        LocalDate endDate = extractEndDate(slice, scopeField);

        if (startDate != null && endDate == null) {
            // 只有开始日期，自动添加结束日期约束
            LocalDate autoEndDate = startDate.plusDays(maxDays);
            Map<String, Object> endFilter = new HashMap<>();
            endFilter.put("field", scopeField);
            endFilter.put("op", "<");
            endFilter.put("value", autoEndDate.toString());
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
    private LocalDate extractStartDate(List<Map<String, Object>> slice, String scopeField) {
        return slice.stream()
                .filter(item -> scopeField.equals(item.get("field")))
                .filter(item -> {
                    String op = String.valueOf(item.get("op"));
                    return ">=".equals(op) || ">".equals(op);
                })
                .map(item -> parseDate(item.get("value")))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 提取结束日期
     */
    private LocalDate extractEndDate(List<Map<String, Object>> slice, String scopeField) {
        return slice.stream()
                .filter(item -> scopeField.equals(item.get("field")))
                .filter(item -> {
                    String op = String.valueOf(item.get("op"));
                    return "<=".equals(op) || "<".equals(op);
                })
                .map(item -> parseDate(item.get("value")))
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
