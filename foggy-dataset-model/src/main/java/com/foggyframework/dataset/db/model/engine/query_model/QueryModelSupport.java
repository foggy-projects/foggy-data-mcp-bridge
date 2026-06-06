package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.AbstractDelegateDecorate;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.order.OrderDef;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.join.JoinEdge;
import com.foggyframework.dataset.db.model.engine.join.JoinGraph;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.DbColumnDelegate;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationOutputColumn;
import com.foggyframework.dataset.db.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.db.model.impl.query.*;
import com.foggyframework.dataset.db.model.impl.utils.QueryObjectDelegate;
import com.foggyframework.dataset.db.model.path.DimensionPath;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.member.SyntheticMemberRuntimeColumn;
import com.foggyframework.dataset.db.model.semantic.member.permission.QmMemberPermissionDef;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.support.QueryColumnGroup;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.JoinType;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Slf4j
public  abstract class QueryModelSupport extends DbObjectSupport implements QueryModel {
    /**
     * selectQueryColumns、或columnGroups
     */
    @Override
    public List<DbQueryColumn> getJdbcQueryColumns() {
        return dbQueryColumns;
    }

    /**
     * 模型短简称，由 JdbcQueryModelLoader 在加载时分配
     * 用于 AI 元数据生成，减少 token 消耗
     */
  protected   String shortAlias;

    protected  List<DbQueryColumn> dbQueryColumns = new ArrayList<>();

    protected   Map<String, DbQueryColumn> nameToJdbcQueryColumn = new HashMap<>();

    protected  List<DbQueryDimension> queryDimensions = new ArrayList<>();

    protected  List<DbQueryProperty> queryProperties = new ArrayList<>();

    protected TableModel jdbcModel;
//
//    SqlFormulaService sqlFormulaService;
//
//    DataSource defaultDataSource;
//
//    MongoTemplate defaultMongoTemplate;

    protected  List<DbQueryCondition> dbQueryConditions;
    protected  Map<String, DbQueryCondition> name2JdbcQueryCond = new HashMap<>();

    protected  List<QueryColumnGroup> columnGroups;

    protected  Map<String, DbQueryAccessImpl> dimToJdbcQueryAccess = new HashMap<>();

    protected  Fsscript fsscript;

    protected   List<DbQueryOrderColumnImpl> orders = new ArrayList<>();

    /**
     * 权限查询构建器列表（简化后的 accesses）
     */
    protected List<FsscriptFunction> accessBuilders = new ArrayList<>();

    /**
     * QM 级成员权限配置列表（内部成员权限）
     */
    protected List<QmMemberPermissionDef> memberPermissions;

    /**
     * QM 预定义的计算字段（formula 项）
     * <p>
     * 在查询时自动注入到 calculatedFields 中。
     * DSL 请求中同名的 calculatedField 可覆盖 QM 预定义的。
     * </p>
     */
    protected List<CalculatedFieldDef> predefinedCalculatedFields = new ArrayList<>();

    protected  List<TableModel> jdbcModelList;

    /**
     * QM 字段 ↔ 物理列双向映射缓存（QM 加载时构建，或首次访问时 lazy init）
     */
    protected volatile PhysicalColumnMapping physicalColumnMapping;

    @Override
    public PhysicalColumnMapping getPhysicalColumnMapping() {
        PhysicalColumnMapping m = this.physicalColumnMapping;
        if (m == null) {
            // Lazy init: 如果 QM 在映射代码加入前已缓存，首次访问时构建
            m = PhysicalColumnMappingBuilder.build(this);
            this.physicalColumnMapping = m;
        }
        return m;
    }

    // 使用 IdentityHashMap：按对象引用（==）而非 equals() 匹配 key
    // 解决自引用维度场景：两个不同的 QueryObject 实例引用同一张物理表时，
    // 如果 equals()/hashCode() 基于字段比较会导致 alias 覆盖
    protected   Map<Object, String> name2Alias = new IdentityHashMap<>();

    /**
     * 合并后的 JoinGraph（延迟初始化，线程安全）
     * <p>
     * 对于单模型：直接引用主模型的 JoinGraph
     * 对于多模型：合并所有模型的 JoinGraph
     * </p>
     */
    private volatile JoinGraph mergedJoinGraph;

    @Getter
    public abstract static class AbstractJdbcModelSupport extends AbstractDelegateDecorate<TableModel> implements TableModel {
        public AbstractJdbcModelSupport(TableModel delegate) {
            super(delegate);
        }

        @Delegate(excludes = AbstractDelegateDecorate.class)
        public TableModel getDelegate() {
            return delegate;
        }


    }

    @Getter
    public static class JdbcModelDx extends AbstractJdbcModelSupport implements TableModel {

        String alias;

        String foreignKey;

        FsscriptFunction onBuilder;

        TableModel dependsOn;

        Map<String, DbColumn> name2JdbcColumn = new HashMap<>();

        QueryObject dxQueryObject;

        JoinType joinType = JoinType.LEFT;

        @Override
        public QueryObject getQueryObject() {
            if (dxQueryObject == null) {
                dxQueryObject = new QueryObjectDelegate(delegate.getQueryObject()) {
                    @Override
                    public String getAlias() {
                        return StringUtils.isEmpty(alias) ? super.getAlias() : alias;
                    }

                    @Override
                    public FsscriptFunction getOnBuilder() {
                        return onBuilder == null ? super.getOnBuilder() : onBuilder;
                    }

                    @Override
                    public QueryObject getLinkQueryObject() {
                        if (dependsOn != null) {
                            return dependsOn.getQueryObject();
                        }
                        return super.getLinkQueryObject();
                    }

                    @Override
                    public String getForeignKey(QueryObject joinObject) {
                        if (StringUtils.isNotEmpty(foreignKey) && JdbcModelDx.this.delegate.getQueryObject().isRootEqual(joinObject)) {
                            return foreignKey;
                        }
                        return super.getForeignKey(joinObject);
                    }

                };
            }
            return dxQueryObject;
        }

        public JdbcModelDx(TableModel delegate, String foreignKey, FsscriptFunction onBuilder, String alias) {
            super(delegate);
            this.foreignKey = foreignKey;
            this.onBuilder = onBuilder;
            this.alias = alias;
        }

        public JdbcModelDx(TableModel delegate, String foreignKey, FsscriptFunction onBuilder, String alias, JoinType joinType) {
            super(delegate);
            this.foreignKey = foreignKey;
            this.onBuilder = onBuilder;
            this.alias = alias;
            this.joinType = joinType;
        }


        public String getAlias() {
            return StringUtils.isEmpty(alias) ? delegate.getAlias() : alias;
        }

        public void addDependsOn(TableModel dm) {
            dependsOn = dm;
        }

        @Override
        public DbColumn findJdbcColumnByName(String jdbcColumName) {
            if (StringUtils.isEmpty(jdbcColumName)) {
                return null;
            }
            DbColumn cached = name2JdbcColumn.get(jdbcColumName);
            if (cached != null) {
                return cached;
            }
            DbColumn column = delegate.findJdbcColumnByName(jdbcColumName);
            if (column == null) {
                return null;
            }
            DbColumn result = shouldAliasColumn(column)
                    ? new AliasBoundDbColumn(column, getQueryObject())
                    : column;
            name2JdbcColumn.put(jdbcColumName, result);
            return result;
        }

        private boolean shouldAliasColumn(DbColumn column) {
            if (column instanceof AggregateRelationOutputColumn) {
                return false;
            }
            return column.getQueryObject() != null
                    && column.getQueryObject().isRootEqual(delegate.getQueryObject());
        }

    }

    private static class AliasBoundDbColumn extends DbColumnDelegate {

        private final QueryObject queryObject;

        AliasBoundDbColumn(DbColumn delegate, QueryObject queryObject) {
            super(delegate);
            this.queryObject = queryObject;
        }

        @Override
        public QueryObject getQueryObject() {
            return queryObject;
        }

        @Override
        public Object getExtData() {
            return delegate.getExtData();
        }

        @Override
        public AiObject getAi() {
            return delegate.getAi();
        }
    }

    public QueryModelSupport(List<TableModel> jdbcModelList, Fsscript fsscript) {
        this.jdbcModel = jdbcModelList.get(0);
        this.fsscript = fsscript;
        this.jdbcModelList = jdbcModelList;
        for (TableModel model : jdbcModelList) {
            // 先注册包装 QueryObject 本身，支持同一 TM 多 alias 的精确匹配。
            name2Alias.put(model.getQueryObject(), model.getAlias());
            // root 仅作为旧列对象的回退，不覆盖已存在 root，避免同一 TM 多别名互相覆盖。
            name2Alias.putIfAbsent(model.getQueryObject().getRoot(), model.getAlias());
        }
    }

    /**
     * 获取合并后的 JoinGraph
     * <p>
     * 线程安全的延迟初始化。对于单模型直接返回主模型的 JoinGraph，
     * 对于多模型则合并所有模型的 JoinGraph 并缓存。
     * </p>
     *
     * @return 合并后的 JoinGraph
     */
    public JoinGraph getMergedJoinGraph() {
        if (mergedJoinGraph == null) {
            synchronized (this) {
                if (mergedJoinGraph == null) {
                    mergedJoinGraph = buildMergedJoinGraph();
                }
            }
        }
        return mergedJoinGraph;
    }

    /**
     * 构建合并后的 JoinGraph
     */
    private JoinGraph buildMergedJoinGraph() {
        JoinGraph baseGraph = jdbcModel.getJoinGraph();

        // 以 QM 包裹模型的 QueryObject 为根复制并合并。
        // 底层 TM 的 JoinGraph root 是原始 TM alias；QM v2 alias 场景下必须换成包裹 alias。
        JoinGraph merged = new JoinGraph(jdbcModel.getQueryObject());
        merged.addRootAlias(baseGraph.getRoot());
        copyModelJoinGraph(merged, jdbcModel, baseGraph);

        if (jdbcModelList == null || jdbcModelList.size() <= 1) {
            return merged;
        }

        for (int i = 1; i < jdbcModelList.size(); i++) {
            TableModel tm = jdbcModelList.get(i);
            JdbcModelDx dx = tm.getDecorate(JdbcModelDx.class);

            // 使用 JdbcModelDx 的 alias 专属 QueryObject，支持同一 TM 多别名。
            QueryObject targetQueryObject = dx.getQueryObject();

            // 确定 FROM 表：优先使用 dependsOn，否则使用主模型的 root
            QueryObject fromQueryObject = (dx.getDependsOn() != null)
                    ? dx.getDependsOn().getQueryObject()
                    : baseGraph.getRoot();

            // 添加主边
            if (dx.getOnBuilder() != null) {
                merged.addEdge(fromQueryObject, targetQueryObject,
                        dx.getOnBuilder(), dx.getJoinType());
            } else if (StringUtils.isNotEmpty(dx.getForeignKey())) {
                merged.addEdge(fromQueryObject, targetQueryObject,
                        dx.getForeignKey());
            }

            // 添加次模型的维度边
            JoinGraph secondaryGraph = tm.getJoinGraph();
            if (secondaryGraph != null) {
                copyModelJoinGraph(merged, tm, secondaryGraph);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("QueryModel [{}] JoinGraph 构建完成: 节点={}, 边={}",
                    name, merged.getNodeCount(), merged.getEdgeCount());
        }

        return merged;
    }

    private void copyModelJoinGraph(JoinGraph targetGraph, TableModel model, JoinGraph sourceGraph) {
        if (sourceGraph == null) {
            return;
        }
        QueryObject originalRoot = sourceGraph.getRoot();
        QueryObject aliasRoot = model.getQueryObject();
        for (JoinEdge edge : sourceGraph.getAllEdges()) {
            targetGraph.addEdge(
                    rebindRootQueryObject(edge.getFrom(), originalRoot, aliasRoot),
                    rebindRootQueryObject(edge.getTo(), originalRoot, aliasRoot),
                    edge.getForeignKey(),
                    edge.getOnBuilder(),
                    edge.getJoinType());
        }
    }

    private QueryObject rebindRootQueryObject(QueryObject queryObject, QueryObject originalRoot, QueryObject aliasRoot) {
        if (queryObject == null || originalRoot == null || aliasRoot == null) {
            return queryObject;
        }
        if (StringUtils.equals(queryObject.getAlias(), originalRoot.getAlias())) {
            return aliasRoot;
        }
        return queryObject;
    }



    @Override
    public TableModel getJdbcModelByQueryObject(QueryObject queryObject) {
        for (TableModel model : this.jdbcModelList) {
            if (model.getQueryObject() == queryObject) {
                return model;
            }
        }
        return null;
    }

    public DbQueryOrderColumnImpl addOrder(DbColumn jdbcColumn, String order) {
        DbQueryOrderColumnImpl c = new DbQueryOrderColumnImpl(jdbcColumn, order);
        orders.add(c);
        return c;
    }

    public DbQueryOrderColumnImpl addOrder(DbColumn jdbcColumn, OrderDef d) {
        DbQueryOrderColumnImpl c = new DbQueryOrderColumnImpl(jdbcColumn, d);
        orders.add(c);
        return c;
    }


    @Override
    public List<DbQueryOrderColumnImpl> getOrders() {
        return orders;
    }

    @Override
    public DbQueryColumn getIdJdbcQueryColumn() {
        String idColumn = jdbcModel.getIdColumn();
        if (StringUtils.isEmpty(idColumn)) {
            return null;
        }
        for (DbQueryColumn dbQueryColumn : dbQueryColumns) {

            if (StringUtils.equalsIgnoreCase(dbQueryColumn.getSelectColumn().getSqlColumn().getName(), idColumn)) {
                return dbQueryColumn;
            }
        }
        return null;
    }

    /**
     * 向 QM 注册一个可查询列。
     *
     * <p>这里有两条不同的注册路径，后续修改时不要混在一起：
     *
     * <p>1. 普通业务 QM 的维度列不能直接按当前列名落入 {@code dbQueryColumns}。
     * 这类列通常先经过维度展开，最终对外暴露为 {@code dim$id}、{@code dim$caption}、
     * 以及嵌套维度路径等稳定别名；如果这里直接注册，会把原有维度语义顶掉，
     * 之前出现过 {@code customer$id -> customer} 这类回归。
     *
     * <p>2. synthetic member-QM 的运行时列是特意构造出来的最终 schema，
     * 例如 {@code id}、{@code caption}、{@code productCategory$id}。
     * 这些列已经是最终对外名称，必须直接注册，不能再走普通维度展开逻辑。
     */
    public void addJdbcQueryColumn(DbQueryColumn dbQueryColumn) {
        if (dbQueryColumns == null) {
            dbQueryColumns = new ArrayList<>();
        }

        boolean syntheticMemberRuntimeColumn = dbQueryColumn.getSelectColumn() instanceof SyntheticMemberRuntimeColumn;

        // 普通 QM 的维度列走旧的维度别名注册逻辑，保持 $id/$caption 和嵌套路径兼容。
        // 只有 synthetic member-QM 的运行时列才允许直接注册。
        if (dbQueryColumn.isDimension() && !syntheticMemberRuntimeColumn) {
            DbDimensionColumn dimensionColumn = dbQueryColumn.getSelectColumn().getDecorate(DbDimensionColumn.class);
            DbDimensionSupport.DimensionCaptionDbColumn support = dbQueryColumn.getSelectColumn().getDecorate(DbDimensionSupport.DimensionCaptionDbColumn.class);

            if (support != null && dimensionColumn != null && dimensionColumn.isCaptionColumn()) {
                DbDimension dbDimension = support.getDimension();
                DbColumn foreignKeyJdbcColumn = support.getDimension().getForeignKeyDbColumn();
                DbColumn captionJdbcColumn = support.getDimension().getCaptionDbColumn();
                registerNestedDimensionAliases(dbDimension, foreignKeyJdbcColumn, captionJdbcColumn, dbQueryColumn.getCaption());
            }
            return;
        }

        registerJdbcQueryColumnDirectly(dbQueryColumn);
    }

    private void registerJdbcQueryColumnDirectly(DbQueryColumn dbQueryColumn) {
        // 直接注册只适用于：
        // 1. 非维度普通列
        // 2. synthetic member-QM 的运行时维度列
        // 这里不再补做维度展开，传入的 name 必须已经是最终对外字段名。
        for (DbQueryColumn selectQueryColumn : dbQueryColumns) {
            if ((selectQueryColumn.getSelectColumn() == dbQueryColumn.getSelectColumn()) && (StringUtils.equals(selectQueryColumn.getName(), dbQueryColumn.getName()))) {
                return;
            }
        }
        DbQueryColumn existing = nameToJdbcQueryColumn.get(dbQueryColumn.getName());
        if (existing != null) {
            if (existing.getSelectColumn() == dbQueryColumn.getSelectColumn()) {
                return;
            }
            throw RX.throwAUserTip(DatasetMessages.querymodelDuplicateQuerycolumn(dbQueryColumn.getName()));
        }
        dbQueryColumns.add(dbQueryColumn);
        nameToJdbcQueryColumn.put(dbQueryColumn.getName(), dbQueryColumn);

    }

    /**
     * 为嵌套维度注册别名和完整路径的访问方式
     *
     * <p>使用 DOT 格式作为内部标准，同时支持 UNDERSCORE 格式查询
     *
     * @param dbDimension        维度
     * @param foreignKeyJdbcColumn 外键列
     * @param captionJdbcColumn    标题列
     * @param caption              标题
     */
    private void registerNestedDimensionAliases(DbDimension dbDimension, DbColumn foreignKeyJdbcColumn, DbColumn captionJdbcColumn, String caption) {
        DimensionPath dimPath = dbDimension.getDimensionPath();
        String path = dimPath.toDotFormat();

        // 使用 DOT 格式注册（内部标准格式）
        String idName = path + "$id";
        String captionName = path + "$caption";

        if (!nameToJdbcQueryColumn.containsKey(idName)) {
            DbQueryColumn idColumn = new DbQueryColumnImpl(foreignKeyJdbcColumn, idName, foreignKeyJdbcColumn.getCaption(), idName);
            nameToJdbcQueryColumn.put(idName, idColumn);
            dbQueryColumns.add(idColumn);
        }
        if (!nameToJdbcQueryColumn.containsKey(captionName)) {
            DbQueryColumn captionColumn = new DbQueryColumnImpl(captionJdbcColumn, captionName, caption, captionName);
            nameToJdbcQueryColumn.put(captionName, captionColumn);
            dbQueryColumns.add(captionColumn);
        }
        registerTimeDimensionRootAlias(dbDimension, path, captionJdbcColumn, caption);

        // 同时用 UNDERSCORE 格式注册（用于前端友好的列名）
        String aliasPath = dimPath.toUnderscoreFormat();
        String aliasIdName = aliasPath + "$id";
        String aliasCaptionName = aliasPath + "$caption";

        if (!nameToJdbcQueryColumn.containsKey(aliasIdName)) {
            DbQueryColumn aliasIdColumn = new DbQueryColumnImpl(foreignKeyJdbcColumn, aliasIdName, foreignKeyJdbcColumn.getCaption(), aliasIdName);
            nameToJdbcQueryColumn.put(aliasIdName, aliasIdColumn);
        }
        if (!nameToJdbcQueryColumn.containsKey(aliasCaptionName)) {
            DbQueryColumn aliasCaptionColumn = new DbQueryColumnImpl(captionJdbcColumn, aliasCaptionName, caption, aliasCaptionName);
            nameToJdbcQueryColumn.put(aliasCaptionName, aliasCaptionColumn);
        }
        registerTimeDimensionRootAlias(dbDimension, aliasPath, captionJdbcColumn, caption);

        // 如果有别名，也用别名注册
        String alias = dbDimension.getAlias();
        if (StringUtils.isNotEmpty(alias) && !alias.equals(path) && !alias.equals(aliasPath)) {
            String aliasBasedIdName = alias + "$id";
            String aliasBasedCaptionName = alias + "$caption";
            if (!nameToJdbcQueryColumn.containsKey(aliasBasedIdName)) {
                DbQueryColumn aliasIdCol = new DbQueryColumnImpl(foreignKeyJdbcColumn, aliasBasedIdName, foreignKeyJdbcColumn.getCaption(), aliasBasedIdName);
                nameToJdbcQueryColumn.put(aliasBasedIdName, aliasIdCol);
            }
            if (!nameToJdbcQueryColumn.containsKey(aliasBasedCaptionName)) {
                DbQueryColumn aliasCaptionCol = new DbQueryColumnImpl(captionJdbcColumn, aliasBasedCaptionName, caption, aliasBasedCaptionName);
                nameToJdbcQueryColumn.put(aliasBasedCaptionName, aliasCaptionCol);
            }
            registerTimeDimensionRootAlias(dbDimension, alias, captionJdbcColumn, caption);
        }

        // 为父子维度注册 hierarchy 视角的列（team$hierarchy$id, team$hierarchy$caption, team$hierarchy$xxx）
        registerParentChildHierarchyColumns(dbDimension, path, aliasPath, alias, caption);
    }

    private void registerTimeDimensionRootAlias(DbDimension dbDimension, String fieldName, DbColumn captionJdbcColumn, String caption) {
        if (StringUtils.isEmpty(fieldName) || captionJdbcColumn == null || nameToJdbcQueryColumn.containsKey(fieldName)) {
            return;
        }
        DbDimensionType type = dbDimension.getType();
        if (type != DbDimensionType.DATETIME && type != DbDimensionType.DAY) {
            return;
        }
        DbQueryColumn rootDateColumn = new DbQueryColumnImpl(captionJdbcColumn, fieldName, caption, fieldName);
        nameToJdbcQueryColumn.put(fieldName, rootDateColumn);
        dbQueryColumns.add(rootDateColumn);
    }

    /**
     * 为父子维度注册层级视角（hierarchy）的列
     *
     * <p>层级汇总视角的列通过 closure.parent_id 关联维度表，用于层级汇总查询
     *
     * @param dbDimension 维度
     * @param path        DOT 格式路径
     * @param aliasPath   UNDERSCORE 格式路径
     * @param alias       维度别名
     * @param caption     标题
     */
    private void registerParentChildHierarchyColumns(DbDimension dbDimension, String path, String aliasPath, String alias, String caption) {
        DbModelParentChildDimensionImpl pcDim = dbDimension.getDecorate(DbModelParentChildDimensionImpl.class);
        if (pcDim == null || pcDim.getHierarchyQueryObject() == null) {
            return;
        }

        DbColumn hierarchyIdColumn = pcDim.getHierarchyPrimaryKeyDbColumn();
        DbColumn hierarchyCaptionColumn = pcDim.getHierarchyCaptionDbColumn();

        if (hierarchyIdColumn == null || hierarchyCaptionColumn == null) {
            return;
        }

        // 使用 DOT 格式注册 hierarchy 列
        String hierarchyIdName = path + "$hierarchy$id";
        String hierarchyCaptionName = path + "$hierarchy$caption";

        if (!nameToJdbcQueryColumn.containsKey(hierarchyIdName)) {
            DbQueryColumn idCol = new DbQueryColumnImpl(hierarchyIdColumn, hierarchyIdName, hierarchyIdColumn.getCaption(), hierarchyIdName);
            nameToJdbcQueryColumn.put(hierarchyIdName, idCol);
            dbQueryColumns.add(idCol);
        }
        if (!nameToJdbcQueryColumn.containsKey(hierarchyCaptionName)) {
            DbQueryColumn captionCol = new DbQueryColumnImpl(hierarchyCaptionColumn, hierarchyCaptionName, caption + "(层级)", hierarchyCaptionName);
            nameToJdbcQueryColumn.put(hierarchyCaptionName, captionCol);
            dbQueryColumns.add(captionCol);
        }

        // 同时用 UNDERSCORE 格式注册
        String aliasHierarchyIdName = aliasPath + "$hierarchy$id";
        String aliasHierarchyCaptionName = aliasPath + "$hierarchy$caption";

        if (!nameToJdbcQueryColumn.containsKey(aliasHierarchyIdName)) {
            DbQueryColumn aliasIdCol = new DbQueryColumnImpl(hierarchyIdColumn, aliasHierarchyIdName, hierarchyIdColumn.getCaption(), aliasHierarchyIdName);
            nameToJdbcQueryColumn.put(aliasHierarchyIdName, aliasIdCol);
        }
        if (!nameToJdbcQueryColumn.containsKey(aliasHierarchyCaptionName)) {
            DbQueryColumn aliasCaptionCol = new DbQueryColumnImpl(hierarchyCaptionColumn, aliasHierarchyCaptionName, caption + "(层级)", aliasHierarchyCaptionName);
            nameToJdbcQueryColumn.put(aliasHierarchyCaptionName, aliasCaptionCol);
        }

        // 如果有别名，也用别名注册
        if (StringUtils.isNotEmpty(alias) && !alias.equals(path) && !alias.equals(aliasPath)) {
            String aliasBasedHierarchyIdName = alias + "$hierarchy$id";
            String aliasBasedHierarchyCaptionName = alias + "$hierarchy$caption";
            if (!nameToJdbcQueryColumn.containsKey(aliasBasedHierarchyIdName)) {
                DbQueryColumn aliasIdCol = new DbQueryColumnImpl(hierarchyIdColumn, aliasBasedHierarchyIdName, hierarchyIdColumn.getCaption(), aliasBasedHierarchyIdName);
                nameToJdbcQueryColumn.put(aliasBasedHierarchyIdName, aliasIdCol);
            }
            if (!nameToJdbcQueryColumn.containsKey(aliasBasedHierarchyCaptionName)) {
                DbQueryColumn aliasCaptionCol = new DbQueryColumnImpl(hierarchyCaptionColumn, aliasBasedHierarchyCaptionName, caption + "(层级)", aliasBasedHierarchyCaptionName);
                nameToJdbcQueryColumn.put(aliasBasedHierarchyCaptionName, aliasCaptionCol);
            }
        }

        // 注册 hierarchy 视角的属性列（team$hierarchy$xxx）
        for (DbDimensionSupport.DimensionPropertyDbColumn propCol : pcDim.getHierarchyPropertyDbColumns()) {
            String propName = propCol.getName(); // 已经是 team$hierarchy$xxx 格式
            if (!nameToJdbcQueryColumn.containsKey(propName)) {
                DbQueryColumn propQueryCol = new DbQueryColumnImpl(propCol, propName, propCol.getCaption(), propName);
                nameToJdbcQueryColumn.put(propName, propQueryCol);
                dbQueryColumns.add(propQueryCol);
            }
        }
    }

    //    public void addSelectColumn(JdbcColumn jdbcColumn) {
//        selectColumns.add(jdbcColumn);
//    }
    @Override
    public DbQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<DbQueryRequestDef> form) {
        // 创建新的上下文
        ModelResultContext context = new ModelResultContext(form, null);
        return query(systemBundlesContext, context);
    }

//    @Override
//    public JdbcQueryResult query(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
//        switch (this.jdbcModel.getModelType()) {
//            case mongo:
//                return queryMongo(systemBundlesContext, context.getRequest());
//            case jdbc:
//            default:
//                return queryJdbc(systemBundlesContext, context);
//        }
//    }



    @Override
    public QueryObject getQueryObject() {
        return jdbcModel.getQueryObject();
    }

    @Override
    public DbColumn findJdbcColumnForCond(String condColumnName, boolean errorIfNotFound) {
        return findJdbcColumnForCond(condColumnName, errorIfNotFound, errorIfNotFound);
    }

    /**
     * @param condColumnName
     * @param errorIfNotFound
     * @param extSearch       当传入true时，会进行扩展搜索，从nameToJdbcQueryColumn抢救下
     * @return
     */
    @Override
    public DbColumn findJdbcColumnForCond(String condColumnName, boolean errorIfNotFound, boolean extSearch) {

        DbQueryCondition cond = name2JdbcQueryCond.get(condColumnName);
        if (cond != null) {
            return cond.getColumn();
        }

        DbQueryColumn qc = this.nameToJdbcQueryColumn.get(condColumnName);
        if (qc != null) {
            return qc.getSelectColumn();
        }

        DbColumn jdbcColumn = findAliasQualifiedJdbcColumn(condColumnName);
        if (jdbcColumn != null) {
            return jdbcColumn;
        }

        for (TableModel model : this.jdbcModelList) {
            jdbcColumn = model.findJdbcColumnByName(condColumnName);
            if (jdbcColumn != null) {
                break;
            }
        }

        if (extSearch && jdbcColumn == null) {
            for (TableModel model : this.jdbcModelList) {
                if (model.isDeprecated(condColumnName)) {
                    return null;
                }
            }

            if (errorIfNotFound) {
                throw RX.throwAUserTip(DatasetMessages.querymodelColumnNotfound(getName(), toJdbcModelListName(), condColumnName, findDimension(condColumnName)));
            }
        }

        return jdbcColumn;
    }

    private String toJdbcModelListName() {
        StringBuilder sb = new StringBuilder();
        for (TableModel model : this.jdbcModelList) {
            sb.append(model.getName()).append(",");
        }
        return sb.toString();
    }

    @Override
    public DbQueryColumn findJdbcColumnForSelectByName(String jdbcColumName, boolean errorIfNotFound) {

        DbQueryColumn queryColumn = nameToJdbcQueryColumn.get(jdbcColumName);
        if (queryColumn != null) {
            return queryColumn;
        }

        for (DbQueryColumn dbQueryColumn : dbQueryColumns) {
            if (StringUtils.equals(dbQueryColumn.getName(), jdbcColumName)) {
                return dbQueryColumn;
            }
        }

        DbColumn aliasQualifiedColumn = findAliasQualifiedJdbcColumn(jdbcColumName);
        if (aliasQualifiedColumn != null) {
            return new DbQueryColumnImpl(aliasQualifiedColumn, jdbcColumName, aliasQualifiedColumn.getCaption(), jdbcColumName);
        }

        /**
         * end ***************************
         */
        if (errorIfNotFound) {
            throw RX.throwAUserTip(DatasetMessages.querymodelColumnNotfoundSimple(this.name, jdbcColumName, findDimension(jdbcColumName)));
        }

        return null;
    }

    @Override
    public DbQueryColumn findJdbcQueryColumnByName(String jdbcColumName, boolean errorIfNotFound) {
        DbQueryColumn queryColumn = nameToJdbcQueryColumn.get(jdbcColumName);
        if (queryColumn != null) {
            return queryColumn;
        }
        for (DbQueryColumn dbQueryColumn : this.dbQueryColumns) {
            if (StringUtils.equals(dbQueryColumn.getSelectColumn().getName(), jdbcColumName)) {
                return dbQueryColumn;
            }
        }

        if (errorIfNotFound) {
            throw RX.throwAUserTip(DatasetMessages.querymodelQuerycolumnNotfound(getName(), jdbcColumName));
        }

        return null;
    }

    @Override
    public DbColumn findJdbcColumn(String name) {

        DbColumn jdbcColumn = findAliasQualifiedJdbcColumn(name);
        if (jdbcColumn != null) {
            return jdbcColumn;
        }

        for (TableModel model : this.jdbcModelList) {
            jdbcColumn = model.findJdbcColumnByName(name);
            if (jdbcColumn != null) {
                break;
            }
        }
        return jdbcColumn;
    }

    @Override
    public DbDimension findDimension(String name) {

        DbDimension dimension = findAliasQualifiedDimension(name);
        if (dimension != null) {
            return dimension;
        }

        for (TableModel model : this.jdbcModelList) {
            dimension = model.findJdbcDimensionByName(name);
            if (dimension != null) {
                break;
            }
        }

        return dimension;
    }

    private DbColumn findAliasQualifiedJdbcColumn(String fieldName) {
        AliasQualifiedName qualifiedName = parseAliasQualifiedName(fieldName);
        if (qualifiedName == null) {
            return null;
        }
        for (TableModel model : this.jdbcModelList) {
            if (!StringUtils.equals(model.getAlias(), qualifiedName.alias())) {
                continue;
            }
            DbColumn column = model.findJdbcColumnByName(qualifiedName.field());
            if (column != null) {
                return column;
            }
        }
        return null;
    }

    private DbDimension findAliasQualifiedDimension(String fieldName) {
        AliasQualifiedName qualifiedName = parseAliasQualifiedName(fieldName);
        if (qualifiedName == null) {
            return null;
        }
        for (TableModel model : this.jdbcModelList) {
            if (!StringUtils.equals(model.getAlias(), qualifiedName.alias())) {
                continue;
            }
            DbDimension dimension = model.findJdbcDimensionByName(qualifiedName.field());
            if (dimension != null) {
                return dimension;
            }
        }
        return null;
    }

    private AliasQualifiedName parseAliasQualifiedName(String fieldName) {
        if (StringUtils.isEmpty(fieldName)) {
            return null;
        }
        int dotIndex = fieldName.indexOf('.');
        if (dotIndex <= 0 || dotIndex >= fieldName.length() - 1) {
            return null;
        }
        String alias = fieldName.substring(0, dotIndex);
        String field = fieldName.substring(dotIndex + 1);
        for (TableModel model : this.jdbcModelList) {
            if (StringUtils.equals(model.getAlias(), alias)) {
                return new AliasQualifiedName(alias, field);
            }
        }
        return null;
    }

    private record AliasQualifiedName(String alias, String field) {
    }

    @Override
    public DbProperty findProperty(String name, boolean errorIfNull) {

        DbProperty p = jdbcModel.findJdbcPropertyByName(name);
        if (p != null) {
            return p;
        }
        for (TableModel model : this.jdbcModelList) {
            p = model.findJdbcPropertyByName(name);
            if (p != null) {
                return p;
            }
        }
        if (errorIfNull) {
            throw new RuntimeException("未能找到属性:" + name);
        }
        return p;
    }

    @Override
    public DbQueryDimension findQueryDimension(String name, boolean errorIfNotFound) {

        for (DbQueryDimension queryDimension : queryDimensions) {
            if (StringUtils.equals(queryDimension.getName(), name)) {
                return queryDimension;
            }
        }
        if (errorIfNotFound) {
            throw RX.throwAUserTip(DatasetMessages.querymodelDimensionNotfound(jdbcModel.getName(), name));
        }
        return null;
    }

    @Override
    public DbQueryProperty findQueryProperty(String name, boolean errorIfNotFound) {

        for (DbQueryProperty queryProperty : queryProperties) {
            if (StringUtils.equals(queryProperty.getName(), name)) {
                return queryProperty;
            }
        }
        if (errorIfNotFound) {
            throw RX.throwAUserTip(DatasetMessages.querymodelPropertyNotfound(this.name, name));
        }
        return null;
    }



    public void addJdbcQueryConds(List<DbQueryCondition> values) {
        if (dbQueryConditions == null) {
            dbQueryConditions = new ArrayList<>();
        }
        dbQueryConditions.addAll(values);
        for (DbQueryCondition value : values) {
            name2JdbcQueryCond.put(value.getName(), value);
        }

    }

    public void addJdbcQueryCond(DbQueryCondition dbQueryCondition) {
        if (dbQueryConditions == null) {
            dbQueryConditions = new ArrayList<>();
        }

        dbQueryConditions.add(dbQueryCondition);
        name2JdbcQueryCond.put(dbQueryCondition.getName(), dbQueryCondition);
    }

    @Override
    @Nullable
    public DbQueryCondition findJdbcQueryCondByField(String field) {
        if (dbQueryConditions == null) {
            return null;
        }
        for (DbQueryCondition dbQueryCondition : dbQueryConditions) {
            if (StringUtils.equals(dbQueryCondition.getField(), field)) {
                return dbQueryCondition;
            }
        }
        return null;

    }

    @Override
    @Nullable
    public DbQueryCondition findJdbcQueryCondByName(String name) {
        if (dbQueryConditions == null) {
            return null;
        }
        for (DbQueryCondition dbQueryCondition : dbQueryConditions) {
            if (StringUtils.equals(dbQueryCondition.getName(), name)) {
                return dbQueryCondition;
            }
        }
        return null;

    }

    @Override
    public List<DbColumn> getSelectColumns(boolean newList) {
        List ll = newList ? dbQueryColumns.stream().collect(Collectors.toList()) : dbQueryColumns;
        return ll;
    }


    public DbQueryDimension addQueryDimensionIfNotExist(DbDimension dbDimension) {
        DbQueryDimension d = findQueryDimension(dbDimension.getName(), false);
        if (d == null) {
            d = new DbQueryDimensionImpl(this, dbDimension);
            queryDimensions.add(d);
        }
        return d;
    }

    public DbQueryProperty addQueryPropertyIfNotExist(DbProperty dbProperty) {
        DbQueryProperty d = findQueryProperty(dbProperty.getName(), false);
        if (d == null) {
            d = new DbQueryPropertyImpl(dbProperty);
            queryProperties.add(d);
        }
        return d;
    }

    @Override
    public String getAlias(QueryObject queryObject) {
        if (queryObject == null) {
            return null;
        }
        if (name2Alias == null) {
            return queryObject.getAlias();
        }
        String alias = name2Alias.get(queryObject);
        if (StringUtils.isNotEmpty(alias)) {
            return alias;
        }
        // 使用 root 作为旧列对象回退；同 TM 多别名必须走包装 QueryObject 精确匹配。
        alias = name2Alias.get(queryObject.getRoot());
        return StringUtils.isNotEmpty(alias) ? alias : queryObject.getAlias();
    }

    @Override
    public List<DbQueryCondition> getDbQueryConds() {
        return dbQueryConditions;
    }

}
