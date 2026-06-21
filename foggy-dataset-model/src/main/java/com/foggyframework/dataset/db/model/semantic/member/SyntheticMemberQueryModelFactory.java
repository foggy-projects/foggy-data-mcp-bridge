package com.foggyframework.dataset.db.model.semantic.member;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.join.JoinGraph;
import com.foggyframework.dataset.db.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.DbColumnDelegate;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.db.model.impl.model.DbTableModelImpl;
import com.foggyframework.dataset.db.model.impl.query.DbQueryConditionImpl;
import com.foggyframework.dataset.db.model.impl.query.DbQueryColumnImpl;
import com.foggyframework.dataset.db.model.interceptor.SqlLoggingInterceptor;
import com.foggyframework.dataset.db.model.plugins.query_execution.QueryExecutionStepExecutor;
import com.foggyframework.dataset.db.model.semantic.member.permission.MemberPermissionDef;
import com.foggyframework.dataset.db.model.semantic.member.permission.MemberPermissionSliceDef;
import com.foggyframework.dataset.db.model.semantic.member.permission.QmMemberPermissionDef;
import com.foggyframework.dataset.db.model.semantic.member.permission.SyntheticMemberEffectivePermission;
import com.foggyframework.dataset.db.model.semantic.member.permission.SyntheticMemberPermissionResolver;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.table.SqlColumn;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.JoinType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * synthetic member-QM 的运行时工厂。
 *
 * <p>阶段2先负责把阶段1解析出的 schema 装载成可执行的 JdbcQueryModel，
 * 让 synthetic member-QM 能进入 QueryFacade 主链。</p>
 */
@Component
@Slf4j
public class SyntheticMemberQueryModelFactory {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Autowired(required = false)
    private SqlLoggingInterceptor sqlLoggingInterceptor;

    @Resource
    private QueryExecutionStepExecutor queryExecutionStepExecutor;

    private final SyntheticMemberPermissionResolver permissionResolver = new SyntheticMemberPermissionResolver();

    public QueryModelSupport build(QueryModel sourceModel,
                                   SyntheticMemberQueryModelDescriptor descriptor) {
        Objects.requireNonNull(sourceModel, "sourceModel cannot be null");
        Objects.requireNonNull(descriptor, "descriptor cannot be null");

        JdbcQueryModelImpl sourceJdbcModel = sourceModel.getDecorate(JdbcQueryModelImpl.class);
        QueryModelSupport sourceSupport = sourceModel.getDecorate(QueryModelSupport.class);

        RX.notNull(sourceJdbcModel, "synthetic member-QM 目前仅支持 JDBC QueryModel");

        DbDimension rootDimension = resolveRootDimension(sourceModel.getJdbcModel(), descriptor.dimensionFieldBase());
        validateForcedSliceFields(sourceSupport, descriptor, rootDimension);
        Map<String, DbDimension> pathToDimension = buildNodeDimensionIndex(rootDimension);

        DbTableModelImpl runtimeTableModel = buildRuntimeTableModel(descriptor, rootDimension, pathToDimension);
        JdbcQueryModelImpl syntheticModel = new JdbcQueryModelImpl(
                List.of(runtimeTableModel),
                sourceSupport != null ? sourceSupport.getFsscript() : null,
                sqlFormulaService,
                sourceJdbcModel.getDataSource()
        );

        syntheticModel.setName(descriptor.syntheticModelName());
        syntheticModel.setCaption(rootDimension.getCaption());
        syntheticModel.setQueryExecutionStepExecutor(queryExecutionStepExecutor);
        if (sqlLoggingInterceptor != null) {
            syntheticModel.setSqlLoggingInterceptor(sqlLoggingInterceptor);
        }

        Map<String, DbColumn> runtimeColumns = new LinkedHashMap<>();
        for (SyntheticMemberFieldSchema field : descriptor.schema().fields()) {
            DbDimension nodeDimension = pathToDimension.get(field.nodePath());
            if (nodeDimension == null) {
                continue;
            }

            DbColumn runtimeColumn = buildRuntimeColumn(field, nodeDimension);
            if (runtimeColumn == null) {
                continue;
            }

            runtimeColumns.put(field.name(), runtimeColumn);

            DbQueryColumnImpl queryColumn = new DbQueryColumnImpl(
                    runtimeColumn,
                    field.name(),
                    buildFieldCaption(field, runtimeColumn),
                    field.name()
            );
            syntheticModel.addJdbcQueryColumn(queryColumn);

            DbQueryConditionImpl queryCondition = new DbQueryConditionImpl();
            queryCondition.setQueryModel(syntheticModel);
            queryCondition.setName(field.name());
            queryCondition.setField(field.name());
            queryCondition.setCaption(buildFieldCaption(field, runtimeColumn));
            queryCondition.setColumn(runtimeColumn);
            if (runtimeColumn instanceof DbDimensionColumn dimensionColumn) {
                queryCondition.setDimension(dimensionColumn.getDimension());
                syntheticModel.addQueryDimensionIfNotExist(dimensionColumn.getDimension());
            }
            if (runtimeColumn instanceof DbPropertyColumn propertyColumn) {
                queryCondition.setProperty(propertyColumn.getProperty());
                syntheticModel.addQueryPropertyIfNotExist(propertyColumn.getProperty());
            }
            syntheticModel.addJdbcQueryCond(queryCondition);
            queryColumn.setDbQueryCondition(queryCondition);
        }

        runtimeTableModel.setColumns(new ArrayList<>(runtimeColumns.values()));
        runtimeTableModel.setName2JdbcColumn(new LinkedHashMap<>(runtimeColumns));

        if (log.isDebugEnabled()) {
            log.debug("synthetic member-QM build: model={}, schemaFields={}, runtimeColumns={}, queryColumns={}",
                    descriptor.syntheticModelName(),
                    descriptor.schema().fields().stream().map(SyntheticMemberFieldSchema::name).toList(),
                    runtimeColumns.keySet(),
                    syntheticModel.getJdbcQueryColumns().stream().map(DbQueryColumn::getName).toList());
        }

        return syntheticModel;
    }

    private DbTableModelImpl buildRuntimeTableModel(SyntheticMemberQueryModelDescriptor descriptor,
                                                    DbDimension rootDimension,
                                                    Map<String, DbDimension> pathToDimension) {
        DbTableModelImpl tableModel = new DbTableModelImpl();
        tableModel.setName(descriptor.syntheticModelName() + "$member");
        tableModel.setCaption(rootDimension.getCaption());
        tableModel.setModelType(DbModelType.jdbc);
        tableModel.setTableName(rootDimension.getQueryObject() != null ? rootDimension.getQueryObject().getName() : null);
        tableModel.setQueryObject(rootDimension.getQueryObject());
        tableModel.setIdColumn(rootDimension.getPrimaryKeyDbColumn() != null
                ? rootDimension.getPrimaryKeyDbColumn().getSqlColumnName()
                : null);
        tableModel.setDimensions(flattenDimensions(rootDimension));
        tableModel.setPathToDimension(buildRelativeDimensionLookup(pathToDimension, rootDimension));
        tableModel.setJoinGraph(buildJoinGraph(rootDimension));
        tableModel.setProperties(Collections.emptyList());
        tableModel.setMeasures(Collections.emptyList());
        tableModel.setColumns(new ArrayList<>());
        tableModel.setName2JdbcColumn(new LinkedHashMap<>());
        return tableModel;
    }

    private DbColumn buildRuntimeColumn(SyntheticMemberFieldSchema field, DbDimension dimension) {
        return switch (field.kind()) {
            case ID -> buildDimensionIdColumn(field.name(), dimension);
            case CAPTION -> buildCaptionColumn(field.name(), dimension);
            case PROPERTY -> buildPropertyColumn(field.name(), dimension);
            case PARENT_ID -> buildParentIdColumn(field.name(), dimension);
            case DEPTH, HAS_CHILDREN -> null;
        };
    }

    private DbColumn buildDimensionIdColumn(String exposedName, DbDimension dimension) {
        DbColumn primaryKeyColumn = dimension.getPrimaryKeyDbColumn();
        QueryObject queryObject = dimension.getQueryObject();
        if (primaryKeyColumn == null || queryObject == null) {
            return null;
        }
        DbColumn delegate = new SyntheticMemberPlainFieldColumn(
                exposedName,
                primaryKeyColumn.getCaption(),
                queryObject,
                primaryKeyColumn.getSqlColumn(),
                DbColumnType.fromJdbcType(primaryKeyColumn.getSqlColumn().getJdbcType())
        );
        return new SyntheticMemberDimensionFieldColumn(exposedName, primaryKeyColumn.getCaption(), delegate, dimension, false);
    }

    private DbColumn buildCaptionColumn(String exposedName, DbDimension dimension) {
        DbColumn captionColumn = dimension.getCaptionDbColumn();
        QueryObject queryObject = dimension.getQueryObject();
        if (captionColumn == null || queryObject == null) {
            return null;
        }
        DbColumn delegate = new SyntheticMemberPlainFieldColumn(
                exposedName,
                captionColumn.getCaption(),
                queryObject,
                captionColumn.getSqlColumn(),
                DbColumnType.fromJdbcType(captionColumn.getSqlColumn().getJdbcType())
        );
        return new SyntheticMemberDimensionFieldColumn(exposedName, captionColumn.getCaption(), delegate, dimension, true);
    }

    private DbColumn buildPropertyColumn(String exposedName, DbDimension dimension) {
        DbProperty property = resolveProperty(dimension, exposedName);
        QueryObject queryObject = dimension.getQueryObject();
        if (queryObject == null) {
            return null;
        }

        if (property == null || property.getPropertyDbColumn() == null) {
            return buildPhysicalFieldColumn(exposedName, queryObject);
        }

        DbColumn delegate = new SyntheticMemberPlainFieldColumn(
                exposedName,
                property.getCaption(),
                queryObject,
                property.getPropertyDbColumn().getSqlColumn(),
                property.getType()
        );
        return new SyntheticMemberPropertyFieldColumn(exposedName, delegate, property);
    }

    private DbColumn buildPhysicalFieldColumn(String exposedName, QueryObject queryObject) {
        String fieldName = leafFieldName(exposedName);
        SqlColumn sqlColumn = resolvePhysicalSqlColumn(queryObject, fieldName);
        if (sqlColumn == null) {
            return null;
        }
        return new SyntheticMemberPlainFieldColumn(
                exposedName,
                StringUtils.isNotEmpty(sqlColumn.getCaption()) ? sqlColumn.getCaption() : exposedName,
                queryObject,
                sqlColumn,
                DbColumnType.fromJdbcType(sqlColumn.getJdbcType())
        );
    }

    private SqlColumn resolvePhysicalSqlColumn(QueryObject queryObject, String fieldName) {
        if (queryObject == null || StringUtils.isEmpty(fieldName)) {
            return null;
        }
        SqlColumn direct = queryObject.getSqlColumn(fieldName, false);
        if (direct != null) {
            return direct;
        }
        String snakeCase = StringUtils.to_sm_string(fieldName);
        if (StringUtils.equals(snakeCase, fieldName)) {
            return null;
        }
        return queryObject.getSqlColumn(snakeCase, false);
    }

    private DbColumn buildParentIdColumn(String exposedName, DbDimension dimension) {
        DbModelParentChildDimensionImpl parentChild = dimension.getDecorate(DbModelParentChildDimensionImpl.class);
        if (parentChild == null || dimension.getQueryObject() == null || StringUtils.isEmpty(parentChild.getParentKey())) {
            return null;
        }
        SqlColumn sqlColumn = dimension.getQueryObject().getSqlColumn(parentChild.getParentKey(), false);
        if (sqlColumn == null) {
            return null;
        }
        return new SyntheticMemberPlainFieldColumn(
                exposedName,
                exposedName,
                dimension.getQueryObject(),
                sqlColumn,
                DbColumnType.fromJdbcType(sqlColumn.getJdbcType())
        );
    }

    private String buildFieldCaption(SyntheticMemberFieldSchema field, DbColumn runtimeColumn) {
        if (runtimeColumn != null && StringUtils.isNotEmpty(runtimeColumn.getCaption())) {
            return runtimeColumn.getCaption();
        }
        return field.name();
    }

    private DbProperty resolveProperty(DbDimension dimension, String fieldName) {
        String propertyName = leafFieldName(fieldName);
        return dimension.findPropertyByName(propertyName);
    }

    private String leafFieldName(String fieldName) {
        String propertyName = fieldName;
        int idx = fieldName.lastIndexOf(SyntheticMemberQueryModelResolver.FIELD_SEPARATOR);
        if (idx >= 0) {
            propertyName = fieldName.substring(idx + 1);
        }
        return propertyName;
    }

    private DbDimension resolveRootDimension(TableModel tableModel, String dimFieldBase) {
        RX.notNull(tableModel, "source QueryModel does not contain jdbc model");
        for (DbDimension dimension : safeDimensions(tableModel.getDimensions())) {
            if (StringUtils.equals(dimension.getEffectiveName(), dimFieldBase)) {
                return dimension;
            }
        }
        throw RX.throwAUserTip("无法解析 synthetic member-QM 根维度: " + dimFieldBase);
    }

    private void validateForcedSliceFields(QueryModelSupport sourceSupport,
                                           SyntheticMemberQueryModelDescriptor descriptor,
                                           DbDimension rootDimension) {
        MemberPermissionDef tmPermission = rootDimension instanceof DbDimensionSupport support
                ? support.getMemberPermission()
                : null;
        QmMemberPermissionDef qmPermission = resolveQmPermission(sourceSupport, descriptor.dimensionFieldBase());
        if (tmPermission == null && qmPermission == null) {
            return;
        }

        SyntheticMemberEffectivePermission effective = permissionResolver.resolve(tmPermission, qmPermission);
        if (effective == null || effective.getForcedSlice() == null || effective.getForcedSlice().isEmpty()) {
            return;
        }

        Map<String, SyntheticMemberFieldSchema> fieldIndex = descriptor.schema().fieldIndex();
        for (MemberPermissionSliceDef sliceDef : effective.getForcedSlice()) {
            String field = sliceDef == null ? null : sliceDef.getField();
            if (StringUtils.isEmpty(field) || fieldIndex == null || !fieldIndex.containsKey(field)) {
                throw RX.throwAUserTip(buildForcedSliceFieldMissingMessage(
                        field,
                        descriptor.sourceModelName(),
                        descriptor.dimensionFieldBase()
                ));
            }
        }
    }

    private QmMemberPermissionDef resolveQmPermission(QueryModelSupport sourceSupport, String dimFieldBase) {
        if (sourceSupport == null || sourceSupport.getMemberPermissions() == null) {
            return null;
        }
        for (QmMemberPermissionDef def : sourceSupport.getMemberPermissions()) {
            if (def != null && StringUtils.equals(def.getDimension(), dimFieldBase)) {
                return def;
            }
        }
        return null;
    }

    private String buildForcedSliceFieldMissingMessage(String field, String sourceModelName, String dimFieldBase) {
        return "synthetic member-QM 内部权限字段不存在: field=" + field
                + ", qmModel=" + sourceModelName
                + ", memberField=" + dimFieldBase + SyntheticMemberQueryModelResolver.FIELD_SEPARATOR + "caption";
    }

    private Map<String, DbDimension> buildNodeDimensionIndex(DbDimension rootDimension) {
        Map<String, DbDimension> pathToDimension = new LinkedHashMap<>();
        collectNodeDimension(pathToDimension, rootDimension, "");
        return pathToDimension;
    }

    private void collectNodeDimension(Map<String, DbDimension> pathToDimension,
                                      DbDimension dimension,
                                      String relativePath) {
        pathToDimension.put(relativePath, dimension);
        for (DbDimension child : safeDimensions(dimension.getChildDimensions())) {
            String childPath = StringUtils.isEmpty(relativePath)
                    ? child.getEffectiveName()
                    : relativePath + SyntheticMemberQueryModelResolver.FIELD_SEPARATOR + child.getEffectiveName();
            collectNodeDimension(pathToDimension, child, childPath);
        }
    }

    private Map<String, DbDimension> buildRelativeDimensionLookup(Map<String, DbDimension> pathToDimension,
                                                                  DbDimension rootDimension) {
        Map<String, DbDimension> lookup = new LinkedHashMap<>();
        lookup.put(rootDimension.getEffectiveName(), rootDimension);

        for (Map.Entry<String, DbDimension> entry : pathToDimension.entrySet()) {
            String path = entry.getKey();
            DbDimension dimension = entry.getValue();
            if (StringUtils.isNotEmpty(path)) {
                lookup.put(path.replace(SyntheticMemberQueryModelResolver.FIELD_SEPARATOR, "."), dimension);
                lookup.put(path.replace(SyntheticMemberQueryModelResolver.FIELD_SEPARATOR, "_"), dimension);
                lookup.put(dimension.getEffectiveName(), dimension);
            }
        }
        return lookup;
    }

    private JoinGraph buildJoinGraph(DbDimension rootDimension) {
        QueryObject rootQueryObject = rootDimension.getQueryObject();
        RX.notNull(rootQueryObject, "REFERENCE_QM 维度必须绑定独立 QueryObject");

        JoinGraph joinGraph = new JoinGraph(rootQueryObject);
        registerHierarchyJoin(joinGraph, rootDimension);
        for (DbDimension child : safeDimensions(rootDimension.getChildDimensions())) {
            registerChildJoin(joinGraph, rootDimension, child);
        }
        return joinGraph;
    }

    private void registerChildJoin(JoinGraph joinGraph, DbDimension parent, DbDimension child) {
        QueryObject parentQueryObject = parent.getQueryObject();
        QueryObject childQueryObject = child.getQueryObject();
        if (parentQueryObject == null || childQueryObject == null) {
            return;
        }

        if (childQueryObject.getOnBuilder() != null) {
            joinGraph.addEdge(parentQueryObject, childQueryObject, childQueryObject.getOnBuilder(), JoinType.LEFT);
        } else {
            joinGraph.addEdge(parentQueryObject, childQueryObject, child.getForeignKey());
        }

        registerHierarchyJoin(joinGraph, child);
        for (DbDimension grandChild : safeDimensions(child.getChildDimensions())) {
            registerChildJoin(joinGraph, child, grandChild);
        }
    }

    private void registerHierarchyJoin(JoinGraph joinGraph, DbDimension dimension) {
        DbModelParentChildDimensionImpl parentChild = dimension.getDecorate(DbModelParentChildDimensionImpl.class);
        QueryObject dimensionQueryObject = dimension.getQueryObject();
        if (parentChild == null || dimensionQueryObject == null || StringUtils.isEmpty(parentChild.getForeignKey())) {
            return;
        }

        if (parentChild.getClosureQueryObject() != null) {
            joinGraph.addEdge(dimensionQueryObject, parentChild.getClosureQueryObject(), parentChild.getForeignKey());
        }
        if (parentChild.getAncestorClosureQueryObject() != null) {
            joinGraph.addEdge(dimensionQueryObject, parentChild.getAncestorClosureQueryObject(), parentChild.getForeignKey());
        }
    }

    private List<DbDimension> flattenDimensions(DbDimension rootDimension) {
        List<DbDimension> result = new ArrayList<>();
        collectDimensions(result, rootDimension);
        return result;
    }

    private void collectDimensions(List<DbDimension> result, DbDimension dimension) {
        result.add(dimension);
        for (DbDimension child : safeDimensions(dimension.getChildDimensions())) {
            collectDimensions(result, child);
        }
    }

    private List<DbDimension> safeDimensions(List<DbDimension> dimensions) {
        return dimensions == null ? List.of() : dimensions;
    }

    private abstract static class SyntheticMemberBaseColumn extends AbstractDecorate implements DbColumn {
        private final String alias;
        private final String name;
        private final String caption;
        private final QueryObject queryObject;
        private final SqlColumn sqlColumn;
        private final DbColumnType type;

        protected SyntheticMemberBaseColumn(String alias,
                                            String caption,
                                            QueryObject queryObject,
                                            SqlColumn sqlColumn,
                                            DbColumnType type) {
            this.alias = alias;
            this.name = alias;
            this.caption = caption;
            this.queryObject = queryObject;
            this.sqlColumn = sqlColumn;
            this.type = type;
        }

        @Override
        public String getAlias() {
            return alias;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getCaption() {
            return caption;
        }

        @Override
        public QueryObject getQueryObject() {
            return queryObject;
        }

        @Override
        public SqlColumn getSqlColumn() {
            return sqlColumn;
        }

        @Override
        public DbColumnType getType() {
            return type;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public boolean _isDeprecated() {
            return false;
        }

        @Override
        public Object getExtData() {
            return null;
        }

        @Override
        public AiObject getAi() {
            return null;
        }
    }

    private static final class SyntheticMemberPlainFieldColumn extends SyntheticMemberBaseColumn implements SyntheticMemberRuntimeColumn {
        private SyntheticMemberPlainFieldColumn(String alias,
                                                String caption,
                                                QueryObject queryObject,
                                                SqlColumn sqlColumn,
                                                DbColumnType type) {
            super(alias, caption, queryObject, sqlColumn, type);
        }
    }

    private static final class SyntheticMemberDimensionFieldColumn extends DbColumnDelegate implements DbDimensionColumn, SyntheticMemberRuntimeColumn {
        private final String alias;
        private final String caption;
        private final DbColumn delegateColumn;
        private final DbDimension dimension;
        private final boolean captionColumn;

        private SyntheticMemberDimensionFieldColumn(String alias,
                                                    String caption,
                                                    DbColumn delegate,
                                                    DbDimension dimension,
                                                    boolean captionColumn) {
            super(delegate);
            this.alias = alias;
            this.caption = caption;
            this.delegateColumn = delegate;
            this.dimension = dimension;
            this.captionColumn = captionColumn;
        }

        @Override
        public String getAlias() {
            return alias;
        }

        @Override
        public String getName() {
            return alias;
        }

        @Override
        public String getField() {
            return alias;
        }

        @Override
        public String getCaption() {
            return StringUtils.isNotEmpty(caption) ? caption : super.getCaption();
        }

        @Override
        public DbDimension getDimension() {
            return dimension;
        }

        @Override
        public boolean isCaptionColumn() {
            return captionColumn;
        }

        @Override
        public boolean isDimension() {
            return true;
        }

        @Override
        public String getDescription() {
            return delegateColumn.getDescription();
        }

        @Override
        public boolean _isDeprecated() {
            return delegateColumn._isDeprecated();
        }

        @Override
        public Object getExtData() {
            return delegateColumn.getExtData();
        }

        @Override
        public AiObject getAi() {
            return delegateColumn.getAi();
        }
    }

    private static final class SyntheticMemberPropertyFieldColumn extends DbColumnDelegate implements DbPropertyColumn, SyntheticMemberRuntimeColumn {
        private final String alias;
        private final DbColumn delegateColumn;
        private final DbProperty property;

        private SyntheticMemberPropertyFieldColumn(String alias, DbColumn delegate, DbProperty property) {
            super(delegate);
            this.alias = alias;
            this.delegateColumn = delegate;
            this.property = property;
        }

        @Override
        public String getAlias() {
            return alias;
        }

        @Override
        public String getName() {
            return alias;
        }

        @Override
        public String getField() {
            return alias;
        }

        @Override
        public DbProperty getProperty() {
            return property;
        }

        @Override
        public boolean isProperty() {
            return true;
        }

        @Override
        public String getDescription() {
            return delegateColumn.getDescription();
        }

        @Override
        public boolean _isDeprecated() {
            return delegateColumn._isDeprecated();
        }

        @Override
        public Object getExtData() {
            return delegateColumn.getExtData();
        }

        @Override
        public AiObject getAi() {
            return delegateColumn.getAi();
        }
    }

}
