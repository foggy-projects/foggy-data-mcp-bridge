package com.foggyframework.dataset.model.semantic.support;

import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.DbDimension;
import com.foggyframework.dataset.model.spi.DbProperty;

import java.util.Locale;
import java.util.Map;

/** Shared read-only interpretation for JSON and both Markdown metadata renderers. */
public record TimeFieldMetadata(String timeRole, String recommendedUse, boolean timeField) {
    public static final String BUSINESS_AXIS_USE = "核心业务时间轴，用于同环比和窗口函数";

    public static TimeFieldMetadata property(DbProperty property) {
        DbColumnType type = property.getPropertyDbColumn().getType();
        boolean temporal = type == DbColumnType.DAY || type == DbColumnType.DATETIME;
        String role = nonBlank(property.getTimeRole());
        if (role == null && temporal) {
            String name = lower(property.getName());
            role = name.startsWith("create") || name.startsWith("write") || name.startsWith("update")
                    ? "system_time" : "business_date";
        }
        return new TimeFieldMetadata(role, nonBlank(property.getRecommendedUse()), temporal || role != null);
    }

    public static TimeFieldMetadata dimension(DbDimension dimension) {
        String role = nonBlank(dimension.getTimeRole());
        if (role == null) {
            // Preserve historical name inference, including encoded date-key dimensions.
            String name = lower(dimension.getName());
            if (name.contains("date") || name.contains("time") || name.contains("calendar")) {
                role = "business_date";
            }
        }
        String use = nonBlank(dimension.getRecommendedUse());
        if (use == null && "business_date".equals(role)) {
            use = BUSINESS_AXIS_USE;
        }
        return new TimeFieldMetadata(role, use, role != null);
    }

    public void addTo(Map<String, Object> field) {
        if (timeRole != null) field.put("timeRole", timeRole);
        if (recommendedUse != null) field.put("recommendedUse", recommendedUse);
    }

    public String displayRole() { return timeRole == null ? "-" : timeRole; }
    public String displayUse() { return recommendedUse == null ? "-" : recommendedUse; }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
