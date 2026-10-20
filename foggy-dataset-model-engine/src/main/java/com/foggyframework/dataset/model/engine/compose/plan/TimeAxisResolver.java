package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.spi.*;
import java.util.ArrayList;
import java.util.List;

/** Resolves an authorized model column to a typed calendar axis, never guessing from values. */
public final class TimeAxisResolver {
    private TimeAxisResolver() {}
    public record Axis(String requestedField, String dateField, String dimension) {
        public boolean contains(String field) {
            return field.equals(requestedField) || field.equals(dateField)
                    || (dimension != null && field.startsWith(dimension + "$"));
        }
    }

    public static Axis resolve(QueryModel model, String field) {
        if (model == null) throw new IllegalArgumentException("TIMEWINDOW_FIELD_NOT_FOUND: " + field);
        DbQueryColumn column = model.findJdbcQueryColumnByName(field, false);
        if (column == null) throw new IllegalArgumentException("TIMEWINDOW_FIELD_NOT_FOUND: " + field);
        String dimension = field.contains("$") ? field.substring(0, field.lastIndexOf('$')) : null;
        if (dimension != null && model.getJdbcModel() != null && model.getJdbcModel().getDimensions() != null) {
            for (DbDimension declared : model.getJdbcModel().getDimensions()) {
                if (!dimension.equals(declared.getEffectiveName())) continue;
                Object calendar = declared.getExtDataValue("calendar");
                if (calendar != null && !java.util.Set.of("gregorian", "iso8601").contains(String.valueOf(calendar).toLowerCase()))
                    throw new IllegalArgumentException("TIMEWINDOW_CALENDAR_UNSUPPORTED: calendar must be Gregorian/ISO for this comparison");
            }
        }
        if (temporal(column)) return new Axis(field, field, dimension);
        if (dimension != null) {
            List<String> candidates = new ArrayList<>();
            for (DbQueryColumn candidate : model.getJdbcQueryColumns()) {
                String name = candidate.getName();
                if (name != null && name.startsWith(dimension + "$") && temporal(candidate)) candidates.add(name);
            }
            if (candidates.size() == 1) return new Axis(field, candidates.get(0), dimension);
            if (candidates.size() > 1) throw new IllegalArgumentException(
                    "TIMEWINDOW_TIME_AXIS_AMBIGUOUS: select the DATE/DATETIME field explicitly: " + candidates);
        }
        throw new IllegalArgumentException("TIMEWINDOW_FIELD_NOT_TIME: " + field
                + " requires a typed DATE/DATETIME column or an unambiguous declared date dimension mapping");
    }

    private static boolean temporal(DbColumn column) {
        return column.getType() == DbColumnType.DAY || column.getType() == DbColumnType.DATETIME;
    }
}
