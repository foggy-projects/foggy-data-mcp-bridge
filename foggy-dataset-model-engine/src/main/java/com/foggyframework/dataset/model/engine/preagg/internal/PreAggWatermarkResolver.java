package com.foggyframework.dataset.model.engine.preagg.internal;

import com.foggyframework.dataset.db.dialect.DbType;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.DbDimension;
import com.foggyframework.dataset.model.spi.DbProperty;
import com.foggyframework.dataset.model.spi.QueryObject;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.model.spi.preagg.TimeGranularity;
import com.foggyframework.dataset.db.table.SqlColumn;

import java.util.Map;
import java.util.Locale;

/**
 * Internal physical contract for pre-aggregation watermarks.
 *
 * <p>This is deliberately outside the public SPI. Query rewriting and the
 * optional materialization Addon must resolve the same configured watermark
 * into two independently proven sides: the column stored in the materialized
 * table and the physical source column (including its owning query object).
 * Semantic names and naming conventions are never accepted as physical proof.</p>
 */
public final class PreAggWatermarkResolver {

    private PreAggWatermarkResolver() {
    }

    /**
     * Resolve both sides of one watermark contract.
     */
    public static Resolution resolve(PreAggregation preAggregation,
                                     TableModel sourceModel,
                                     PreAggRefreshDef refreshConfig) {
        String configured = requireWatermark(refreshConfig);
        int delimiter = configured.indexOf('$');
        if (delimiter < 0) {
            return resolveBare(preAggregation, sourceModel, configured);
        }
        validateSemanticSyntax(configured, delimiter);
        ResolvedSemantic semantic = resolveSemantic(
                preAggregation,
                sourceModel,
                configured.substring(0, delimiter),
                configured.substring(delimiter + 1));
        return new Resolution(
                configured,
                semantic.materializedColumn(),
                semantic.sourceColumn(),
                semantic.dimension(),
                preAggregation.getGranularity(semantic.dimension().getName()));
    }

    /**
     * Resolve only the materialized side. Used by the bounded DELETE before a
     * source model is needed; it still requires an explicit mapping.
     */
    public static String resolveMaterialized(PreAggregation preAggregation,
                                             PreAggRefreshDef refreshConfig) {
        String configured = requireWatermark(refreshConfig);
        int delimiter = configured.indexOf('$');
        if (delimiter < 0) {
            requireDeclaredMaterializedPhysicalColumn(preAggregation, configured);
            return configured;
        }
        validateSemanticSyntax(configured, delimiter);
        return requireExplicitMaterializedColumn(preAggregation, configured);
    }

    /**
     * Incremental refresh and hybrid query currently bind LocalDate bounds.
     * A native DATE is direct proof. SQLite exposes its supported ISO date
     * representation as TEXT, so that representation is accepted only for the
     * caption of a dimension carrying an explicit time role. Numeric keys and
     * arbitrary text properties remain outside the LocalDate domain. The
     * materialized dimension must remain at exact DAY grain; coarser buckets
     * need bucket-aligned boundaries that this contract does not yet provide.
     */
    @Deprecated
    public static void requireLocalDateBounds(Resolution resolution) {
        requireLocalDateBounds(resolution, null);
    }

    /**
     * Dialect-aware LocalDate boundary validation.
     */
    public static void requireLocalDateBounds(Resolution resolution, FDialect dialect) {
        DbColumnType type = resolution != null ? resolution.sourceColumn().type() : null;
        boolean nativeDate = type == DbColumnType.DAY;
        boolean governedTextDate = resolution != null
                && dialect != null
                && dialect.getDbType() == DbType.SQLITE
                && (type == DbColumnType.TEXT || type == DbColumnType.STRING)
                && resolution.configured().endsWith("$caption")
                && resolution.dimension() != null
                && isDateRole(resolution.dimension().getTimeRole());
        if (!nativeDate && !governedTextDate) {
            String configured = resolution != null ? resolution.configured() : "null";
            throw contractError(
                    "LocalDate bounds require a governed DATE source watermark: "
                            + configured + " is " + type);
        }
        if (resolution.materializedGranularity() != TimeGranularity.DAY) {
            throw contractError(
                    "LocalDate bounds require exact DAY materialized granularity: "
                            + resolution.configured() + " is "
                            + resolution.materializedGranularity());
        }
    }

    private static Resolution resolveBare(PreAggregation preAggregation,
                                          TableModel sourceModel,
                                          String configured) {
        String targetSemanticField = requireDeclaredMaterializedPhysicalColumn(
                preAggregation, configured);
        QueryObject sourceQueryObject = sourceModel != null ? sourceModel.getQueryObject() : null;
        SqlColumn physicalSourceColumn = sourceQueryObject != null
                ? sourceQueryObject.getSqlColumn(configured, false)
                : null;
        if (physicalSourceColumn == null) {
            throw contractError(
                    "No source physical column is declared for bare watermark " + configured);
        }
        SourceColumn sourceColumn = new SourceColumn(
                configured,
                DbColumnType.fromJdbcType(physicalSourceColumn.getJdbcType()),
                sourceQueryObject);
        ResolvedSemantic targetDescriptor = resolveSemanticField(
                preAggregation, sourceModel, targetSemanticField);
        if (targetDescriptor.sourceColumn().type() != sourceColumn.type()) {
            throw contractError(
                    "Bare watermark source/target types differ for " + configured
                            + ": source=" + sourceColumn.type()
                            + ", target=" + targetDescriptor.sourceColumn().type());
        }
        DbDimension dimension = targetDescriptor.dimension();
        return new Resolution(
                configured,
                configured,
                sourceColumn,
                dimension,
                dimension != null ? preAggregation.getGranularity(dimension.getName()) : null);
    }

    private static ResolvedSemantic resolveSemanticField(PreAggregation preAggregation,
                                                         TableModel sourceModel,
                                                         String semanticField) {
        int delimiter = semanticField != null ? semanticField.indexOf('$') : -1;
        if (delimiter <= 0 || delimiter == semanticField.length() - 1
                || semanticField.indexOf('$', delimiter + 1) >= 0) {
            throw contractError(
                    "Invalid explicit dimension mapping field: " + semanticField);
        }
        return resolveSemantic(
                preAggregation,
                sourceModel,
                semanticField.substring(0, delimiter),
                semanticField.substring(delimiter + 1));
    }

    private static ResolvedSemantic resolveSemantic(PreAggregation preAggregation,
                                                    TableModel sourceModel,
                                                    String dimensionName,
                                                    String propertyName) {
        DbDimension dimension = requireDimension(
                preAggregation, sourceModel, dimensionName);
        String semanticField = dimensionName + "$" + propertyName;
        String materializedColumn = requireExplicitMaterializedColumn(
                preAggregation, semanticField);
        if ("id".equals(propertyName)) {
            return new ResolvedSemantic(
                    semanticField,
                    materializedColumn,
                    resolveFactForeignKey(dimension, sourceModel, semanticField),
                    dimension);
        }

        DbColumn sourceColumn;
        if ("caption".equals(propertyName)) {
            sourceColumn = dimension.getCaptionDbColumn();
        } else {
            DbProperty property = dimension.findPropertyByName(propertyName);
            sourceColumn = property != null ? property.getPropertyDbColumn() : null;
        }
        if (sourceColumn == null || isBlank(sourceColumn.getSqlColumnName())
                || sourceColumn.getQueryObject() == null || sourceColumn.getType() == null) {
            throw contractError(
                    "No complete source physical column is declared for " + semanticField);
        }
        return new ResolvedSemantic(
                semanticField,
                materializedColumn,
                new SourceColumn(
                        sourceColumn.getSqlColumnName(),
                        sourceColumn.getType(),
                        sourceColumn.getQueryObject()),
                dimension);
    }

    private static SourceColumn resolveFactForeignKey(DbDimension dimension,
                                                      TableModel sourceModel,
                                                      String semanticField) {
        String foreignKey = dimension.getForeignKey();
        DbColumn foreignKeyColumn = dimension.getForeignKeyDbColumn();
        if (isBlank(foreignKey) || sourceModel == null || sourceModel.getQueryObject() == null) {
            throw contractError(
                    "No source fact-table foreign key is declared for " + semanticField);
        }
        if (foreignKeyColumn != null && !isBlank(foreignKeyColumn.getSqlColumnName())
                && !foreignKey.equals(foreignKeyColumn.getSqlColumnName())) {
            throw contractError(
                    "Conflicting source fact-table foreign keys are declared for "
                            + semanticField);
        }
        DbColumnType type = foreignKeyColumn != null ? foreignKeyColumn.getType() : null;
        if (type == null && dimension.getPrimaryKeyDbColumn() != null) {
            type = dimension.getPrimaryKeyDbColumn().getType();
        }
        if (type == null) {
            throw contractError("No source type is declared for " + semanticField);
        }
        return new SourceColumn(foreignKey, type, sourceModel.getQueryObject());
    }

    private static DbDimension requireDimension(PreAggregation preAggregation,
                                                TableModel sourceModel,
                                                String dimensionName) {
        if (preAggregation == null || sourceModel == null || isBlank(dimensionName)
                || preAggregation.getDimensionNames() == null
                || !preAggregation.getDimensionNames().contains(dimensionName)) {
            throw contractError("Unknown pre-aggregation dimension: " + dimensionName);
        }
        DbDimension dimension = sourceModel.findJdbcDimensionByName(dimensionName);
        if (dimension == null) {
            throw contractError("No source dimension is declared for " + dimensionName);
        }
        return dimension;
    }

    private static String requireExplicitMaterializedColumn(PreAggregation preAggregation,
                                                            String semanticField) {
        Map<String, String> mappings = preAggregation != null
                ? preAggregation.getExplicitDimensionPropertyColumnNames()
                : null;
        String physicalColumn = mappings != null ? mappings.get(semanticField) : null;
        if (isBlank(physicalColumn)) {
            throw contractError(
                    "No explicit materialized column is declared for " + semanticField);
        }
        return physicalColumn;
    }

    private static String requireDeclaredMaterializedPhysicalColumn(
            PreAggregation preAggregation,
            String physicalColumn) {
        Map<String, String> mappings = preAggregation != null
                ? preAggregation.getExplicitDimensionPropertyColumnNames()
                : null;
        String semanticField = null;
        if (mappings != null) {
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                if (!isBlank(entry.getValue()) && entry.getValue().equals(physicalColumn)) {
                    if (semanticField != null) {
                        throw contractError(
                                "Bare watermark maps to more than one semantic field: "
                                        + physicalColumn);
                    }
                    semanticField = entry.getKey();
                }
            }
        }
        if (isBlank(semanticField)) {
            throw contractError(
                    "Bare watermark is not a declared materialized column: "
                            + physicalColumn);
        }
        return semanticField;
    }

    private static String requireWatermark(PreAggRefreshDef refreshConfig) {
        String configured = refreshConfig != null ? refreshConfig.getWatermarkColumn() : null;
        if (isBlank(configured)) {
            throw contractError("Incremental refresh requires an explicit watermark column");
        }
        return configured;
    }

    private static void validateSemanticSyntax(String configured, int delimiter) {
        if (delimiter == 0 || delimiter == configured.length() - 1
                || configured.indexOf('$', delimiter + 1) >= 0) {
            throw contractError("Invalid semantic watermark: " + configured);
        }
    }

    private static IllegalArgumentException contractError(String message) {
        return new IllegalArgumentException(
                "Pre-aggregation physical column contract violation: " + message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isDateRole(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "date".equals(normalized) || normalized.endsWith("_date");
    }

    public record SourceColumn(String physicalName,
                               DbColumnType type,
                               QueryObject queryObject) {
    }

    public record Resolution(String configured,
                             String materializedColumn,
                             SourceColumn sourceColumn,
                             DbDimension dimension,
                             TimeGranularity materializedGranularity) {
    }

    private record ResolvedSemantic(String semanticField,
                                    String materializedColumn,
                                    SourceColumn sourceColumn,
                                    DbDimension dimension) {
    }
}
