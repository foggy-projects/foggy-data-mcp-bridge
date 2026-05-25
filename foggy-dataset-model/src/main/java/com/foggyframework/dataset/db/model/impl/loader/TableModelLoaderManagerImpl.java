package com.foggyframework.dataset.db.model.impl.loader;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.conversion.FsscriptConversionService;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.def.DbModelDef;
import com.foggyframework.dataset.db.model.def.dimension.DbCaptionDef;
import com.foggyframework.dataset.db.model.def.dimension.DbDimensionDef;
import com.foggyframework.dataset.db.model.def.measure.DbFormulaDef;
import com.foggyframework.dataset.db.model.def.measure.DbMeasureDef;
import com.foggyframework.dataset.db.model.def.preagg.PreAggregationDef;
import com.foggyframework.dataset.db.model.def.property.DbPropertyDef;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.dataset.db.model.engine.query_model.DbModelFileChangeHandler;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.dialect.DbType;
import com.foggyframework.dataset.db.model.impl.LoaderSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelDimensionImpl;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelTimeDimensionImpl;
import com.foggyframework.dataset.db.model.impl.measure.DbMeasureSupport;
import com.foggyframework.dataset.db.model.impl.measure.DbModelMeasureImpl;
import com.foggyframework.dataset.db.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.db.model.impl.preagg.PreAggregationImpl;
import com.foggyframework.dataset.db.model.impl.property.DbPropertyImpl;
import com.foggyframework.dataset.db.model.impl.utils.QueryObjectSupport;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.utils.DbUtils;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Setter
@Getter
public class TableModelLoaderManagerImpl extends LoaderSupport implements TableModelLoaderManager {
    @Resource
    DataSource dataSource;

    /**
     * Named data source resolver (optional).
     * Used to resolve dataSourceName in TM definitions.
     */
    NamedDataSourceResolver namedDataSourceResolver;

//    /**
//     * MongoDB 模型加载器（可选）
//     * <p>仅当项目配置了 MongoDB（存在 MongoClient Bean）时自动注入
//     */
//    @Autowired(required = false)
//    MongoModelLoader mongoModelLoader;

    DbModelFileChangeHandler fileChangeHandler;
    List<DbModelLoadProcessor> processors;
    DatasetProperties datasetProperties;

    Map<String, TableModel> name2JdbcModel = new HashMap<>();
    Map<String, TableModelLoader> typeName2Loader = new HashMap<>();
    int dimIdx;
    int modelIdx;

    public TableModelLoaderManagerImpl(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader, List<DbModelLoadProcessor> processors, List<TableModelLoader> loaders) {
        super(systemBundlesContext, fileFsscriptLoader);
        this.processors = processors;
        this.datasetProperties = new DatasetProperties();
        loaders.forEach(loader -> typeName2Loader.put(loader.getTypeName(), loader));
    }

    public TableModelLoaderManagerImpl(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader, List<DbModelLoadProcessor> processors, List<TableModelLoader> loaders, NamedDataSourceResolver namedDataSourceResolver) {
        this(systemBundlesContext, fileFsscriptLoader, processors, loaders, namedDataSourceResolver, null);
    }

    public TableModelLoaderManagerImpl(SystemBundlesContext systemBundlesContext,
                                       FileFsscriptLoader fileFsscriptLoader,
                                       List<DbModelLoadProcessor> processors,
                                       List<TableModelLoader> loaders,
                                       NamedDataSourceResolver namedDataSourceResolver,
                                       DatasetProperties datasetProperties) {
        this(systemBundlesContext, fileFsscriptLoader, processors, loaders);
        this.namedDataSourceResolver = namedDataSourceResolver;
        if (datasetProperties != null) {
            this.datasetProperties = datasetProperties;
        }
    }

    @Override
    public void clearAll() {
        name2JdbcModel = new HashMap<>();
        log.debug("已清除所有命名空间的TableModel缓存");
    }

    @Override
    public void clearByNamespace(String namespace) {
        String normalizedNs = (namespace == null || namespace.trim().isEmpty()) ? "" : namespace.trim();

        if (normalizedNs.isEmpty()) {
            // 清除默认命名空间的缓存（不含冒号的key）
            // 注意：不能在 stream 遍历 keySet 的同时 remove，HashMap 会抛 ConcurrentModificationException
            List<String> keysToRemove = name2JdbcModel.keySet().stream()
                    .filter(key -> !key.contains(":"))
                    .collect(java.util.stream.Collectors.toList());
            keysToRemove.forEach(name2JdbcModel::remove);
            log.info("已清除默认命名空间的TableModel缓存，共 {} 个模型", keysToRemove.size());
        } else {
            // 清除指定命名空间的缓存（以 "namespace:" 开头的key）
            String prefix = normalizedNs + ":";
            List<String> keysToRemove = name2JdbcModel.keySet().stream()
                    .filter(key -> key.startsWith(prefix))
                    .collect(java.util.stream.Collectors.toList());
            keysToRemove.forEach(name2JdbcModel::remove);
            log.info("已清除命名空间 [{}] 的TableModel缓存，共 {} 个模型", normalizedNs, keysToRemove.size());
        }
    }

    @Override
    synchronized public TableModel load(String name) {
        return load(name, null);
    }

    @Override
    synchronized public TableModel load(String modelName, String namespace) {
        String fullName = buildFullName(namespace, modelName);

        TableModel tm = name2JdbcModel.get(fullName);
        if (tm != null) {
            return tm;
        }

        Fsscript fScript = this.findFsscript(modelName, "tm", namespace);
        ExpEvaluator ee = fScript.eval(systemBundlesContext.getApplicationContext());
        Object model = ee.getExportObject("model");
        if (model == null) {
            throw RX.throwAUserTip(DatasetMessages.modelNotFound(modelName));
        }
        Bundle bundle = fScript.getFsscriptClosureDefinition().getFsscriptClosureDefinitionSpace().getBundle();
        DbModelDef def = FsscriptConversionService.getSharedInstance().convert(model, DbModelDef.class);
        applySemanticScalePolicy(def, namespace);
        fix(def);

        // Resolve data source before loading (needed by JdbcTableModelLoaderImpl)
        DataSource effectiveDataSource = resolveDataSource(def);
        if (effectiveDataSource != null) {
            def.setDataSource(effectiveDataSource);
        }

        TableModelLoader tableModelLoader = typeName2Loader.get(def.getType());
        if (tableModelLoader == null) {
            String typeName = def.getType();
            String hint = getLoaderDependencyHint(typeName);
            throw RX.throwAUserTip(DatasetMessages.loaderNotFound(typeName, hint));
        }
        tm = tableModelLoader.load(fScript, def, bundle);
        tm = initialization(tm, def, bundle);

        name2JdbcModel.put(fullName, tm);
        return tm;
    }

    /**
     * 构建完整的模型名称（包含namespace）
     *
     * @param namespace 命名空间（空字符串或null表示默认命名空间）
     * @param modelName 模型名称
     * @return 完整名称（格式：namespace:modelName 或 modelName）
     */
    private String buildFullName(String namespace, String modelName) {
        String normalizedNs = normalizeNamespace(namespace);
        if (normalizedNs.isEmpty()) {
            return modelName;
        }
        return normalizedNs + ":" + modelName;
    }

    private void applySemanticScalePolicy(DbModelDef def, String namespace) {
        if (isSemanticScaleEnabled(namespace)) {
            return;
        }
        clearSemanticScale(def);
        if (log.isDebugEnabled()) {
            log.debug("semanticScaleFactor disabled for namespace [{}], model [{}]",
                    normalizeNamespace(namespace), def.getName());
        }
    }

    private boolean isSemanticScaleEnabled(String namespace) {
        DatasetProperties.SemanticScaleConfig config = datasetProperties == null
                ? null
                : datasetProperties.getSemanticScale();
        if (config == null) {
            return true;
        }

        String normalizedNs = normalizeNamespace(namespace);
        List<String> disabledNamespaces = config.getDisabledNamespaces();
        if (disabledNamespaces != null) {
            for (String disabledNamespace : disabledNamespaces) {
                if (normalizedNs.equals(normalizeNamespace(disabledNamespace))) {
                    return false;
                }
            }
        }
        return config.isDefaultEnabled();
    }

    private String normalizeNamespace(String namespace) {
        return namespace == null || namespace.trim().isEmpty() ? "" : namespace.trim();
    }

    private void clearSemanticScale(DbModelDef def) {
        if (def.getProperties() != null) {
            for (DbPropertyDef property : def.getProperties()) {
                if (property != null) {
                    property.setSemanticScaleFactor(null);
                }
            }
        }
        if (def.getMeasures() != null) {
            for (DbMeasureDef measure : def.getMeasures()) {
                if (measure != null) {
                    measure.setSemanticScaleFactor(null);
                }
            }
        }
        if (def.getDimensions() != null) {
            for (DbDimensionDef dimension : def.getDimensions()) {
                clearSemanticScale(dimension);
            }
        }
    }

    private void clearSemanticScale(DbDimensionDef dimension) {
        if (dimension == null) {
            return;
        }
        if (dimension.getProperties() != null) {
            for (DbPropertyDef property : dimension.getProperties()) {
                if (property != null) {
                    property.setSemanticScaleFactor(null);
                }
            }
        }
        if (dimension.getDimensions() != null) {
            for (DbDimensionDef child : dimension.getDimensions()) {
                clearSemanticScale(child);
            }
        }
    }

    /**
     * 根据类型名称获取依赖提示信息
     */
    private String getLoaderDependencyHint(String typeName) {
        if ("mongo".equalsIgnoreCase(typeName)) {
            return "foggy-dataset-model-mongo";
        }
        // 可扩展其他类型
        return null;
    }

    /**
     * Resolve data source for model loading.
     *
     * <p>Priority: dataSourceName (resolved via NamedDataSourceResolver) > def.dataSource > default dataSource
     *
     * @param def Model definition
     * @return Resolved DataSource
     */
    private DataSource resolveDataSource(DbModelDef def) {
        // 1. Try to resolve by name
        if (StringUtils.isNotEmpty(def.getDataSourceName()) && namedDataSourceResolver != null) {
            DataSource namedDs = namedDataSourceResolver.resolve(def.getDataSourceName());
            if (namedDs != null) {
                log.debug("Using named data source: {} for model: {}", def.getDataSourceName(), def.getName());
                return namedDs;
            }
            log.warn("Named data source '{}' not found for model '{}', falling back to default",
                    def.getDataSourceName(), def.getName());
        }

        // 2. Use dataSource from definition
        if (def.getDataSource() != null) {
            return def.getDataSource();
        }

        // 3. Use default dataSource
        return this.dataSource;
    }

    private void fix(DbModelDef def) {
        if (def.getProperties() != null) {
            for (DbPropertyDef property : def.getProperties()) {
                if (property == null) {
                    continue;
                }
                if (StringUtils.isNotEmpty(property.getName()) && StringUtils.isEmpty(property.getAlias())) {
                    property.setAlias(property.getName());
                }
            }
        }
        if (def.getMeasures() != null) {
            for (DbMeasureDef measure : def.getMeasures()) {
                if (measure == null) {
                    continue;
                }
                if (StringUtils.isNotEmpty(measure.getName()) && StringUtils.isEmpty(measure.getAlias())) {
                    measure.setAlias(measure.getName());
                }
            }
        }

        if (StringUtils.isEmpty(def.getType())) {
            def.setType("jdbc");
        }

    }

    public TableModel initialization(TableModel jm, DbModelDef def, Bundle bundle) {
        RX.notNull(def, "加载模型时的def不得为空");

        // Resolve data source: dataSourceName > def.dataSource > default dataSource
        DataSource effectiveDataSource = resolveDataSource(def);
        RX.notNull(effectiveDataSource, "加载模型时的数据源不得为空");

        String tableName = def.getTableName();
        String viewSql = def.getViewSql();
        TableModelSupport jdbcModel = jm.getDecorate(TableModelSupport.class);
//        JdbcModelImpl jdbcModel = new JdbcModelImpl(dataSource,fScript);
//        def.apply(jdbcModel);
//        jdbcModel.setMongoTemplate(defMongoTemplate);

//        jdbcModel.setQueryObject(loadQueryObject(dataSource, jdbcModel.getModelType() == JdbcModelType.mongo ? null : tableName, viewSql, def.getSchema()));
        /**
         * 加入JSON列的支持,目前先让属性和度量支持
         */
        if (def.getMeasures() != null) {
            for (DbMeasureDef measure : def.getMeasures()) {
                if (measure != null && StringUtils.isNotEmpty(measure.getColumn()) && measure.getColumn().indexOf("->") > 0) {
                    jdbcModel.getQueryObject().appendSqlColumn(measure.getColumn(), "OBJECT", 0);
                }
            }
        }
        if (def.getProperties() != null) {
            for (DbPropertyDef measure : def.getProperties()) {
                if (measure != null && StringUtils.isNotEmpty(measure.getColumn()) && measure.getColumn().indexOf("->") > 0) {
                    jdbcModel.getQueryObject().appendSqlColumn(measure.getColumn(), "OBJECT", 0);
                }
            }
        }

        JdbcModelLoadContext context = new JdbcModelLoadContext(effectiveDataSource, def, jdbcModel, bundle);
        //加载维度定义
        loadDimensions(context);

        loadProperties(context);
        //加载度量
        loadMeasures(context);

        //加载预聚合配置
        loadPreAggregations(context);

        //初始化主表、维表或相关的 alias
        initAlias(context);

        jdbcModel.init();
//        initDimension(context);

//        if
//        dialect.getColumnsByTableName()

        return jdbcModel;
    }

    private void initAlias(JdbcModelLoadContext context) {
//        idx++;
        String d = "d";
        String m = "m";
        QueryObject qo = context.getJdbcModel().getQueryObject();
        qo.getDecorate(QueryObjectSupport.class).setAlias(m + (++modelIdx));

        for (DbDimension dimension : context.getJdbcModel().getDimensions()) {
            QueryObject dqo = dimension.getQueryObject();
            if (dqo == null) {
                continue;
            }
            dqo.getDecorate(QueryObjectSupport.class).setAlias(d + (++dimIdx));
            if (dimension.getDecorate(DbModelParentChildDimensionImpl.class) != null) {
                DbModelParentChildDimensionImpl pcDim = dimension.getDecorate(DbModelParentChildDimensionImpl.class);
                // 为闭包表分配别名（后代方向）
                pcDim.getClosureQueryObject().getDecorate(QueryObjectSupport.class).setAlias(d + (++dimIdx));
                // 为闭包表分配别名（祖先方向）
                if (pcDim.getAncestorClosureQueryObject() != null) {
                    pcDim.getAncestorClosureQueryObject().getDecorate(QueryObjectSupport.class).setAlias(d + (++dimIdx));
                }
                // 为层级视角维度表分配别名
                if (pcDim.getHierarchyQueryObject() != null) {
                    pcDim.getHierarchyQueryObject().getDecorate(QueryObjectSupport.class).setAlias(d + (++dimIdx));
                }
            }

        }
    }

    private void loadDimensions(JdbcModelLoadContext context) {
        DbModelDef def = context.getDef();
        List<DbDimensionDef> dimensionDefList = def.getDimensions();
        if (dimensionDefList != null) {
            //加载在Model上定义的维度
            dimensionDefList = dimensionDefList.stream().filter(e -> e != null).collect(Collectors.toList());
            for (DbDimensionDef dimensionDef : dimensionDefList) {
                DbDimension dbDimension = loadDimension(context, dimensionDef, true);
            }
        }

        if (def.isAutoLoadDimensions()) {
            //TODO 自动加载维度
        }

        /**
         * 初始化维度的相关列
         */
        for (DbDimension dimension : context.getJdbcModel().getDimensions()) {

            DbDimensionSupport ds = dimension.getDecorate(DbDimensionSupport.class);

            //初始化维度的相关列
            ds.init();
        }

    }


    private void loadProperties(JdbcModelLoadContext context) {
        DbModelDef def = context.getDef();
        List<DbPropertyDef> jdbcPropertyDefList = def.getProperties();
        if (jdbcPropertyDefList != null) {
            jdbcPropertyDefList = jdbcPropertyDefList.stream().filter(e -> e != null).collect(Collectors.toList());
            for (DbPropertyDef propertyDef : jdbcPropertyDefList) {
                try {
                    DbProperty dbProperty = loadProperty(context, null, propertyDef);
                    context.getJdbcModel().addJdbcProperty(dbProperty);
                } catch (Throwable t) {
                    log.error("加载属性发生错误", t);
                    if (propertyDef.isDeprecated()) {
                        log.warn("忽略被标记为废弃的属性:{}", propertyDef.getName());
                        context.getJdbcModel().addDeprecated(propertyDef);
                    } else {
                        throw ErrorUtils.toRuntimeException(t);
                    }
                }
            }
        }
    }


    private void loadMeasures(JdbcModelLoadContext context) {
        DbModelDef def = context.getDef();
        List<DbMeasureDef> measureDefList = def.getMeasures();
        if (measureDefList != null) {
            //加载在Model上定义的维度
            measureDefList = measureDefList.stream().filter(e -> e != null).collect(Collectors.toList());
            for (DbMeasureDef measureDef : measureDefList) {
                try {
                    loadMeasure(context, measureDef);
                } catch (Throwable t) {
                    log.error("加载度量发生错误", t);
                    if (measureDef.isDeprecated()) {
                        log.warn("忽略被标记为废弃的度量:{}", measureDef.getName());
                        context.getJdbcModel().addDeprecated(measureDef);
                    } else {
                        throw ErrorUtils.toRuntimeException(t);
                    }
                }

            }
        }

        if (def.isAutoLoadDimensions()) {
            //TODO 自动加载维度
        }

    }

    /**
     * 加载预聚合配置
     *
     * @param context 加载上下文
     */
    private void loadPreAggregations(JdbcModelLoadContext context) {
        DbModelDef def = context.getDef();
        List<PreAggregationDef> preAggDefs = def.getPreAggregations();
        if (preAggDefs == null || preAggDefs.isEmpty()) {
            return;
        }

        TableModelSupport jdbcModel = context.getJdbcModel().getDecorate(TableModelSupport.class);
        for (PreAggregationDef preAggDef : preAggDefs) {
            if (preAggDef == null) {
                continue;
            }
            try {
                PreAggregationImpl preAgg = new PreAggregationImpl(preAggDef, null);
                jdbcModel.getPreAggregations().add(preAgg);
                if (log.isDebugEnabled()) {
                    log.debug("加载预聚合配置: name={}, tableName={}, priority={}",
                            preAgg.getName(), preAgg.getTableName(), preAgg.getPriority());
                }
            } catch (Exception e) {
                log.error("加载预聚合配置失败: {}", preAggDef.getName(), e);
                throw ErrorUtils.toRuntimeException(e);
            }
        }

        log.info("模型 {} 加载了 {} 个预聚合配置", jdbcModel.getName(), jdbcModel.getPreAggregations().size());
    }

    private DbDimension loadDimension(JdbcModelLoadContext context, DbDimensionDef dimensionDef, boolean modelDim) {
        return loadDimension(context, dimensionDef, modelDim, null);
    }

    /**
     * 加载维度（支持嵌套维度）
     *
     * @param context         加载上下文
     * @param dimensionDef    维度定义
     * @param modelDim        是否添加到模型的维度列表
     * @param parentDimension 父维度（如果是嵌套维度）
     * @return 加载后的维度
     */
    private DbDimension loadDimension(JdbcModelLoadContext context, DbDimensionDef dimensionDef, boolean modelDim, DbDimension parentDimension) {

        /**
         * 检查数据
         */
        if (context.getJdbcModel().findJdbcDimensionByName(dimensionDef.getName()) != null) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateDimension(dimensionDef.getName()));
        }

        /**
         * 开始加载维度
         */
        DbDimensionSupport dimension = null;
        if (DbDimensionType.DATETIME == DbDimensionType.fromString(dimensionDef.getType())) {
            //时间维
            dimension = new DbModelTimeDimensionImpl();
        } else if (StringUtils.isNotEmpty(dimensionDef.getParentKey())) {
            //父子结构~
            DbModelParentChildDimensionImpl parentChildDimension = new DbModelParentChildDimensionImpl(dimensionDef.getParentKey(), dimensionDef.getChildKey(), dimensionDef.getClosureTableName());
            dimension = parentChildDimension;
            // 加载闭包表 - use context.getDataSource() to inherit from model's dataSourceName
            parentChildDimension.setClosureQueryObject(loadQueryObject(dimensionDef.getDataSource() == null ? context.getDataSource() : dimensionDef.getDataSource(), dimensionDef.getClosureTableName(), null, dimensionDef.getClosureTableSchema()));
            //childKey用来作为ClosureQueryObject的primaryKey与主表进行关联，注意，childKey实际上可不是主键
            parentChildDimension.getClosureQueryObject().getDecorate(QueryObjectSupport.class).setPrimaryKey(dimensionDef.getChildKey());
            // 加载祖先方向闭包表（ancestorClosureQueryObject），PK 设为 parentKey
            // 用于 selfAndAncestorsOf / ancestorsOf 操作符：fact.FK = closure.parentKey, WHERE closure.childKey = value
            parentChildDimension.setAncestorClosureQueryObject(loadQueryObject(dimensionDef.getDataSource() == null ? context.getDataSource() : dimensionDef.getDataSource(), dimensionDef.getClosureTableName(), null, dimensionDef.getClosureTableSchema()));
            parentChildDimension.getAncestorClosureQueryObject().getDecorate(QueryObjectSupport.class).setPrimaryKey(dimensionDef.getParentKey());
            // 加载层级视角的维度表（hierarchyQueryObject），用于 team$hierarchy$xxx 列
            // hierarchyQueryObject 与 queryObject 是同一个表，但通过 closure.parent_id 关联
            if (StringUtils.isNotEmpty(dimensionDef.getTableName()) || StringUtils.isNotEmpty(dimensionDef.getViewSql())) {
                QueryObject hierarchyQo = loadQueryObject(dimensionDef.getDataSource() == null ? context.getDataSource() : dimensionDef.getDataSource(), dimensionDef.getTableName(), dimensionDef.getViewSql(), dimensionDef.getSchema());
                hierarchyQo.getDecorate(QueryObjectSupport.class).setPrimaryKey(dimensionDef.getPrimaryKey());
                parentChildDimension.setHierarchyQueryObject(hierarchyQo);
            }
        } else {
            dimension = new DbModelDimensionImpl();
        }

//        BeanUtils.copyProperties(dimensionDef, dimension);
        dimensionDef.apply(dimension);
        if (StringUtils.isEmpty(dimension.getAlias())) {
            dimension.setAlias(dimension.getName());
        }
        if (StringUtils.isEmpty(dimension.getKeyCaption())) {
            dimension.setKeyCaption(dimension.getCaption() + "主键");
        }

        // 解析 caption 公式（dialectFormulaDef > formulaDef > 无公式）
        resolveCaptionFormula(context, dimensionDef, dimension);

        // 设置父维度（如果是嵌套维度）
        if (parentDimension != null) {
            dimension.setParentDimension(parentDimension);
        }

        //加载维表
        if (StringUtils.isNotEmpty(dimensionDef.getTableName()) || StringUtils.isNotEmpty(dimensionDef.getViewSql())) {
            //有维表，或视图
            dimension.setQueryObject(loadQueryObject(dimensionDef.getDataSource() == null ? context.getDataSource() : dimensionDef.getDataSource(), dimensionDef.getTableName(), dimensionDef.getViewSql(), dimensionDef.getSchema()));
            dimension.getQueryObject().getDecorate(QueryObjectSupport.class).setPrimaryKey(dimension.getPrimaryKey());
        }

        if (dimensionDef.getProperties() != null) {
            for (DbPropertyDef propertyDef : dimensionDef.getProperties()) {
                DbProperty dbProperty = loadProperty(context, dimension, propertyDef);
                dimension.addProperty(dbProperty);
            }
        }

        processJdbcDataProvider(dimension.getDataProvider());

        DbDimension dbDimension = dimension;
        for (DbModelLoadProcessor processor : processors) {
            dbDimension = processor.processDimension(context, dbDimension);
        }
        if (modelDim) {
            context.getJdbcModel().addDimension(dbDimension);
        }

        // 递归加载嵌套子维度
        if (dimensionDef.getDimensions() != null && !dimensionDef.getDimensions().isEmpty()) {
            for (DbDimensionDef childDef : dimensionDef.getDimensions()) {
                DbDimension childDimension = loadDimension(context, childDef, true, dbDimension);
                dbDimension.addChildDimension(childDimension);
            }
        }

        return dbDimension;
    }

    /**
     * 解析维度的 caption 公式。
     * <p>
     * 优先级：captionDef.dialectFormulaDef[当前数据库类型] > captionDef.formulaDef > 无公式（使用 column 原样输出）
     * </p>
     */
    private void resolveCaptionFormula(JdbcModelLoadContext context, DbDimensionDef dimensionDef, DbDimensionSupport dimension) {
        DbCaptionDef captionDef = dimensionDef.getCaptionDef();
        if (captionDef == null) {
            return;
        }
        DataSource ds = dimensionDef.getDataSource() == null ? context.getDataSource() : dimensionDef.getDataSource();
        FsscriptFunction builder = resolveDialectFormula(ds, captionDef.getFormulaDef(), captionDef.getDialectFormulaDef());
        if (builder != null) {
            dimension.setCaptionFormulaBuilder(builder);
        }
    }

    /**
     * 通用方言公式解析。
     * <p>
     * 优先级：dialectFormulaDef[当前数据库类型] > formulaDef > null
     * </p>
     * <p>
     * 适用于维度 captionDef、属性 formulaDef/dialectFormulaDef、度量 formulaDef/dialectFormulaDef。
     * </p>
     *
     * @param ds                数据源（用于检测方言类型）
     * @param formulaDef        通用公式定义（可为 null）
     * @param dialectFormulaDef 方言专属公式 Map（可为 null）
     * @return 解析后的 FsscriptFunction builder，或 null
     */
    private FsscriptFunction resolveDialectFormula(DataSource ds, DbFormulaDef formulaDef, java.util.Map<String, DbFormulaDef> dialectFormulaDef) {
        // 1. 尝试方言专属公式
        if (dialectFormulaDef != null && !dialectFormulaDef.isEmpty()) {
            DbType dbType = DbUtils.getDialect(ds).getDbType();
            String dbTypeKey = dbType.name().toLowerCase(); // postgresql, mysql, sqlserver, sqlite, oracle
            DbFormulaDef dialectFormula = dialectFormulaDef.get(dbTypeKey);
            if (dialectFormula != null && dialectFormula.getBuilder() != null) {
                return dialectFormula.getBuilder();
            }
        }

        // 2. 回退到通用公式
        if (formulaDef != null && formulaDef.getBuilder() != null) {
            return formulaDef.getBuilder();
        }

        return null;
    }

    private void processJdbcDataProvider(DbDataProvider dataProvider) {
        if (DbDimensionType.DICT == dataProvider.getDimensionType()) {
            RX.notNull(dataProvider.getExtData(), String.format("字典类型的维%s，必须有extData", dataProvider.getName()));

            String dictClass = dataProvider.getExtDataValue("dictClass");
            RX.hasText(dictClass, String.format("字典类型的维%s，必须有extData.dictClass", dataProvider.getName()));
            String dictName = dictClass.substring(dictClass.lastIndexOf(".") + 1);

            dataProvider.getExtData().put("dictName", dictName);
        }
    }

    private DbProperty loadProperty(JdbcModelLoadContext context, DbDimensionSupport dimension, DbPropertyDef propertyDef) {


        DbPropertyImpl property = new DbPropertyImpl();
        propertyDef.apply(property);
//        if (StringUtils.isNotEmpty(property.getType())) {
//            property.setType(property.getType());
//        }
        if (DbColumnType.DAY == property.getType()) {
            if (StringUtils.isEmpty(property.getFormat())) {
                property.setFormat("YYYY-MM-DD");
            }
        }

        property.setTableModel(context.getJdbcModel());
        property.setDbDimension(dimension);
        property.validateSemanticScaleContract(propertyDef.getFormulaDef(), propertyDef.getDialectFormulaDef());
        property.init();
        if (property.getDictionaryDiscovery() != null) {
            property.getDictionaryDiscovery().validate("property " + context.getJdbcModel().getName() + "." + property.getName());
        }

        processJdbcDataProvider(property.getDataProvider());

        DbProperty dbProperty = property;

        // 解析方言公式：dialectFormulaDef[dbType] > formulaDef > 无公式
        DataSource propDs = context.getDataSource();
        FsscriptFunction propFormulaBuilder = resolveDialectFormula(propDs, propertyDef.getFormulaDef(), propertyDef.getDialectFormulaDef());
        if (propFormulaBuilder != null) {
            dbProperty.setFormulaBuilder(propFormulaBuilder);
        }
//        /**
//         * 加入维度支持
//         */
//        if(propertyDef.getDim()!=null){
//            JdbcDimension jdbcDimension = loadDimension(context, propertyDef.getDim(),true);
//
//        }


        for (DbModelLoadProcessor processor : processors) {
            dbProperty = processor.processProperty(context, dbProperty);
        }

        return dbProperty;
    }

    private void loadMeasure(JdbcModelLoadContext context, DbMeasureDef measureDef) {

        /**
         * 检查数据
         */
        if (context.getJdbcModel().findJdbcMeasureByName(measureDef.getName()) != null) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateMeasure(measureDef.getName()));
        }

        /**
         * 开始加载维度
         */
        DbModelMeasureImpl measure = new DbModelMeasureImpl();
        measureDef.apply(measure);
//        if (StringUtils.isNotEmpty(measure.getType())) {
//            measure.setType(measure.getType().toUpperCase());
//        }
        if (StringUtils.isEmpty(measureDef.getAggregation()) && StringUtils.equalsIgnoreCase("money", measureDef.getType())) {
            //如果未定义Aggregation，且是money类型，默认sum
            measureDef.setAggregation("sum");
        }
        if (StringUtils.isNotEmpty(measureDef.getAggregation())) {
            measure.getDecorate(DbMeasureSupport.class).setAggregation(DbAggregation.valueOf(measureDef.getAggregation().toUpperCase()));
        }

        // 解析方言公式：dialectFormulaDef[dbType] > formulaDef > 无公式
        DataSource measureDs = context.getDataSource();
        FsscriptFunction measureFormulaBuilder = resolveDialectFormula(measureDs, measureDef.getFormulaDef(), measureDef.getDialectFormulaDef());
        if (measureFormulaBuilder != null) {
            measure.getDecorate(DbMeasureSupport.class).setFormulaBuilder(measureFormulaBuilder);
        }

        measure.getDecorate(DbMeasureSupport.class).init(context.getJdbcModel(), measureDef);

        DbMeasure jdbcMeasure = measure;
        for (DbModelLoadProcessor processor : processors) {
            jdbcMeasure = processor.processMeasure(context, jdbcMeasure);
        }

        context.getJdbcModel().addMeasure(jdbcMeasure);
//
//        //加载维表
//        dimension.setQueryObject(loadQueryObject(dimensionDef.getTableName(),dimensionDef.getViewSql()));

    }
}
