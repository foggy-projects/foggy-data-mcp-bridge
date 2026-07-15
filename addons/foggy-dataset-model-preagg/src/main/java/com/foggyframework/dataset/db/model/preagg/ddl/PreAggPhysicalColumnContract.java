package com.foggyframework.dataset.db.model.preagg.ddl;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.engine.preagg.internal.PreAggWatermarkResolver;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.DbProperty;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;

import java.util.Map;

/**
 * Resolves the physical columns used by pre-aggregation materialization.
 *
 * <p>The source model column and the materialized table column are different
 * contracts. This resolver deliberately never derives either side from a
 * semantic field name.</p>
 */
final class PreAggPhysicalColumnContract {

    private PreAggPhysicalColumnContract() {
    }

    static ResolvedColumn resolveDimensionId(PreAggregation preAgg,
                                             TableModel sourceModel,
                                             String dimensionName) {
        DbDimension dimension = requireDimension(preAgg, sourceModel, dimensionName);
        String semanticField = semanticField(dimensionName, "id");
        String materializedColumn = requireExplicitMaterializedColumn(preAgg, semanticField);
        return new ResolvedColumn(
                semanticField,
                resolveFactForeignKey(dimension, sourceModel, semanticField),
                materializedColumn,
                dimension
        );
    }

    static ResolvedColumn resolveDimensionGrain(PreAggregation preAgg,
                                                TableModel sourceModel,
                                                String dimensionName,
                                                TimeGranularity granularity) {
        if (granularity == null || granularity == TimeGranularity.DAY) {
            return resolveDimensionId(preAgg, sourceModel, dimensionName);
        }

        ResolvedColumn caption = resolveDimensionProperty(
                preAgg, sourceModel, dimensionName, "caption");
        DbColumnType sourceType = caption.sourceColumn().type();
        if (sourceType != DbColumnType.DAY && sourceType != DbColumnType.DATETIME) {
            throw contractError(
                    "Time bucket " + dimensionName + " requires a DATE/DATETIME source caption");
        }
        return caption;
    }

    static ResolvedColumn resolveDimensionProperty(PreAggregation preAgg,
                                                   TableModel sourceModel,
                                                   String dimensionName,
                                                   String propertyName) {
        DbDimension dimension = requireDimension(preAgg, sourceModel, dimensionName);
        String semanticField = semanticField(dimensionName, propertyName);
        String materializedColumn = requireExplicitMaterializedColumn(preAgg, semanticField);

        if ("id".equals(propertyName)) {
            return new ResolvedColumn(
                    semanticField,
                    resolveFactForeignKey(dimension, sourceModel, semanticField),
                    materializedColumn,
                    dimension
            );
        }

        DbColumn sourceColumn;
        if ("caption".equals(propertyName)) {
            sourceColumn = dimension.getCaptionDbColumn();
        } else {
            DbProperty property = dimension.findPropertyByName(propertyName);
            sourceColumn = property != null ? property.getPropertyDbColumn() : null;
        }

        if (sourceColumn == null || isBlank(sourceColumn.getSqlColumnName())) {
            throw contractError("No source physical column is declared for " + semanticField);
        }
        return new ResolvedColumn(
                semanticField,
                new SourceColumn(
                        sourceColumn.getSqlColumnName(),
                        sourceColumn.getType(),
                        sourceColumn.getQueryObject()
                ),
                materializedColumn,
                dimension
        );
    }

    static WatermarkColumns resolveWatermark(PreAggregation preAgg,
                                             TableModel sourceModel,
                                             PreAggRefreshDef refreshConfig,
                                             FDialect dialect) {
        PreAggWatermarkResolver.Resolution resolved = PreAggWatermarkResolver.resolve(
                preAgg, sourceModel, refreshConfig);
        PreAggWatermarkResolver.requireLocalDateBounds(resolved, dialect);
        return new WatermarkColumns(
                resolved.materializedColumn(),
                new SourceColumn(
                        resolved.sourceColumn().physicalName(),
                        resolved.sourceColumn().type(),
                        resolved.sourceColumn().queryObject()),
                resolved.dimension()
        );
    }

    static String resolveMaterializedWatermark(PreAggregation preAgg,
                                               PreAggRefreshDef refreshConfig) {
        return PreAggWatermarkResolver.resolveMaterialized(preAgg, refreshConfig);
    }

    private static SourceColumn resolveFactForeignKey(DbDimension dimension,
                                                      TableModel sourceModel,
                                                      String semanticField) {
        String foreignKey = dimension.getForeignKey();
        DbColumn foreignKeyColumn = dimension.getForeignKeyDbColumn();
        if (isBlank(foreignKey)) {
            throw contractError("No source fact-table foreign key is declared for " + semanticField);
        }
        if (foreignKeyColumn != null && !isBlank(foreignKeyColumn.getSqlColumnName())
                && !foreignKey.equals(foreignKeyColumn.getSqlColumnName())) {
            throw contractError(
                    "Conflicting source fact-table foreign keys are declared for " + semanticField);
        }

        DbColumnType type = foreignKeyColumn != null ? foreignKeyColumn.getType() : null;
        if (type == null && dimension.getPrimaryKeyDbColumn() != null) {
            type = dimension.getPrimaryKeyDbColumn().getType();
        }
        // A dimension foreign key is, by definition, on the fact/source side.
        // Do not reuse the dimension primary-key QueryObject here.
        QueryObject sourceQueryObject = sourceModel.getQueryObject();
        return new SourceColumn(foreignKey, type, sourceQueryObject);
    }

    private static DbDimension requireDimension(PreAggregation preAgg,
                                                TableModel sourceModel,
                                                String dimensionName) {
        if (preAgg == null || sourceModel == null || isBlank(dimensionName)
                || preAgg.getDimensionNames() == null
                || !preAgg.getDimensionNames().contains(dimensionName)) {
            throw contractError("Unknown pre-aggregation dimension: " + dimensionName);
        }
        DbDimension dimension = sourceModel.findJdbcDimensionByName(dimensionName);
        if (dimension == null) {
            throw contractError("No source dimension is declared for " + dimensionName);
        }
        return dimension;
    }

    private static String requireExplicitMaterializedColumn(PreAggregation preAgg,
                                                            String semanticField) {
        Map<String, String> explicitMappings = preAgg != null
                ? preAgg.getExplicitDimensionPropertyColumnNames()
                : null;
        String physicalColumn = explicitMappings != null
                ? explicitMappings.get(semanticField)
                : null;
        if (isBlank(physicalColumn)) {
            throw contractError(
                    "No explicit materialized column is declared for " + semanticField);
        }
        return physicalColumn;
    }

    private static String semanticField(String dimensionName, String propertyName) {
        if (isBlank(propertyName)) {
            throw contractError("Dimension property name must not be blank: " + dimensionName);
        }
        return dimensionName + "$" + propertyName;
    }

    private static IllegalArgumentException contractError(String message) {
        return new IllegalArgumentException("Pre-aggregation physical column contract violation: " + message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    record SourceColumn(String physicalName, DbColumnType type, QueryObject queryObject) {
    }

    record ResolvedColumn(String semanticField,
                          SourceColumn sourceColumn,
                          String materializedColumn,
                          DbDimension dimension) {
    }

    record WatermarkColumns(String materializedColumn,
                            SourceColumn sourceColumn,
                            DbDimension dimension) {
    }
}
