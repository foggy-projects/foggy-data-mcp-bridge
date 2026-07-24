package com.foggyframework.dataset.model.engine.query_model;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.proxy.ColumnRef;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbDimension;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.TableModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves a V2 {@link ColumnRef} against the TableModel instance that created it.
 *
 * <p>A ColumnRef keeps its source {@code TableModelProxy}.  Its public field name
 * intentionally stays unqualified unless a QM author declares a public alias, but
 * that public-schema rule must not discard ownership while the QM is being lowered.
 * In a multi-model QM, resolving the resulting bare name through {@link QueryModel}
 * can otherwise bind a same-named root-model column.</p>
 */
public final class ColumnRefResolver {

    private ColumnRefResolver() {
    }

    /**
     * Resolve a column only within the TableModel instance owned by {@code columnRef}.
     * Returns {@code null} when that owner does not expose the requested column.
     */
    public static DbColumn resolveColumn(QueryModel queryModel, ColumnRef columnRef) {
        for (TableModel model : resolveOwnerModels(queryModel, columnRef)) {
            DbColumn column = findColumn(model, columnRef);
            if (column != null) {
                return column;
            }
        }
        return null;
    }

    /**
     * Resolve a dimension path only within the TableModel instance owned by
     * {@code columnRef}.
     */
    public static DbDimension resolveDimension(QueryModel queryModel, ColumnRef columnRef) {
        if (columnRef == null || columnRef.getDimensionPath() == null) {
            return null;
        }

        String dotPath = columnRef.getDimensionPath().toDotFormat();
        String aliasPath = columnRef.getDimensionPath().toUnderscoreFormat();
        for (TableModel model : resolveOwnerModels(queryModel, columnRef)) {
            DbDimension dimension = model.findJdbcDimensionByName(dotPath);
            if (dimension == null && !StringUtils.equals(dotPath, aliasPath)) {
                dimension = model.findJdbcDimensionByName(aliasPath);
            }
            if (dimension != null) {
                return dimension;
            }
        }
        return null;
    }

    /**
     * Finds a column among a concrete TableModel's native V2 lookup forms.
     */
    public static DbColumn findColumn(TableModel model, ColumnRef columnRef) {
        if (model == null || columnRef == null) {
            return null;
        }
        for (String candidate : candidateColumnNames(columnRef)) {
            DbColumn column = model.findJdbcColumnByName(candidate);
            if (column != null) {
                return column;
            }
        }
        return null;
    }

    /**
     * Return the concrete QM TableModel instances that own this V2 reference.
     * A declared runtime alias is exact; it must never fall back to another
     * instance of the same TM.
     */
    public static List<TableModel> resolveOwnerModels(QueryModel queryModel, ColumnRef columnRef) {
        if (queryModel == null || columnRef == null || queryModel.getJdbcModelList() == null) {
            return List.of();
        }

        String modelName = columnRef.getModelName();
        String runtimeAlias = columnRef.getTableAlias();
        List<TableModel> matches = new ArrayList<>();
        for (TableModel model : queryModel.getJdbcModelList()) {
            if (model == null || !StringUtils.equals(modelName, model.getName())) {
                continue;
            }
            if (StringUtils.isNotEmpty(runtimeAlias) && !StringUtils.equals(runtimeAlias, model.getAlias())) {
                continue;
            }
            matches.add(model);
        }
        return matches;
    }

    private static Set<String> candidateColumnNames(ColumnRef columnRef) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(columnRef.getFullRef());
        candidates.add(columnRef.getAliasRef());
        candidates.add(columnRef.getColumnName());
        candidates.removeIf(StringUtils::isEmpty);
        return candidates;
    }
}
