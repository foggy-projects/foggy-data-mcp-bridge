package com.foggyframework.dataset.model.impl.query;

import com.foggyframework.dataset.model.spi.*;
import com.foggyframework.dataset.model.plugins.result_set_filter.FieldAccessPermissionStep;
import java.util.*;

/** Concrete QM naming overlays, retaining source identity for metadata and permissions. */
public final class QmColumnNaming {
    private QmColumnNaming() {}

    public static List<DbQueryColumn> overrides(QueryModel model) {
        if (model.getJdbcQueryColumns() == null) return List.of();
        return model.getJdbcQueryColumns().stream()
                .filter(c -> c instanceof DbQueryColumnImpl impl && impl.getNamingSourceReference() != null)
                .toList();
    }

    public static String sourceMetadataName(DbQueryColumn column) {
        DbPropertyColumn property = column.getSelectColumn().getDecorate(DbPropertyColumn.class);
        DbDimensionColumn dimension = column.getSelectColumn().getDecorate(DbDimensionColumn.class);
        if (dimension != null && property == null) {
            return dimension.getDimension().getEffectiveName() + (dimension.isCaptionColumn() ? "$caption" : "$id");
        }
        if (property != null) return property.getProperty().getName();
        return column.getSelectColumn().getName();
    }

    public static boolean belongsTo(DbQueryColumn column, TableModel model) {
        DbDimensionColumn dimension = column.getSelectColumn().getDecorate(DbDimensionColumn.class);
        if (dimension != null && model.getDimensions() != null
                && model.getDimensions().stream().anyMatch(d -> d == dimension.getDimension())) return true;
        DbPropertyColumn property = column.getSelectColumn().getDecorate(DbPropertyColumn.class);
        if (property != null && model.getProperties() != null
                && model.getProperties().stream().anyMatch(p -> p == property.getProperty())) return true;
        return column.getQueryObject() != null && model.getQueryObject() != null
                && column.getQueryObject().isRootEqual(model.getQueryObject());
    }

    public static String permissionName(String name) {
        return name == null ? null : FieldAccessPermissionStep.stripDimensionSuffix(name);
    }

    public static boolean exposesMeasure(QueryModel model, DbMeasure measure) {
        DbQueryColumn column = model.findJdbcColumnForSelectByName(measure.getName(), false);
        if (column == null || !column.isMeasure()) return false;
        DbMeasureColumn source = column.getSelectColumn().getDecorate(DbMeasureColumn.class);
        return source != null && source.getJdbcMeasure() == measure;
    }

    public static boolean overridesProperty(QueryModel model, DbProperty property) {
        // A QM may expose both the original property and a renamed projection.
        // Only suppress the TM-derived metadata entry when its input name is no longer declared.
        if (model.getJdbcQueryColumns() != null && model.getJdbcQueryColumns().stream().anyMatch(c -> {
            DbPropertyColumn source = c.getSelectColumn().getDecorate(DbPropertyColumn.class);
            return Objects.equals(c.getName(), property.getName())
                    && source != null && source.getProperty() == property;
        })) return false;
        return overrides(model).stream().anyMatch(c -> {
            DbPropertyColumn source = c.getSelectColumn().getDecorate(DbPropertyColumn.class);
            return source != null && source.getProperty() == property;
        });
    }
}
