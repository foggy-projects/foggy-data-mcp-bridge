package com.foggyframework.dataset.model.impl.loader;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.conversion.FsscriptConversionService;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.def.DbModelDef;
import com.foggyframework.dataset.model.def.dimension.DbCaptionDef;
import com.foggyframework.dataset.model.def.dimension.DbDimensionDef;
import com.foggyframework.dataset.model.def.measure.DbFormulaDef;
import com.foggyframework.dataset.model.def.measure.DbMeasureDef;
import com.foggyframework.dataset.model.def.preagg.PreAggregationDef;
import com.foggyframework.dataset.model.def.property.DbPropertyDef;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.dataset.model.engine.query_model.DbModelFileChangeHandler;
import com.foggyframework.dataset.model.i18n.DatasetMessages;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogBuildView;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.lifecycle.catalog.StaleCatalogBuildException;
import com.foggyframework.dataset.model.lifecycle.concurrent.ModelBuildKey;
import com.foggyframework.dataset.model.lifecycle.concurrent.ModelBuildSingleFlight;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.model.lifecycle.port.StaleDatasourceBindingException;
import com.foggyframework.dataset.db.dialect.DbType;
import com.foggyframework.dataset.model.impl.LoaderSupport;
import com.foggyframework.dataset.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.model.impl.dimension.DbModelDimensionImpl;
import com.foggyframework.dataset.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.model.impl.dimension.DbModelTimeDimensionImpl;
import com.foggyframework.dataset.model.impl.measure.DbMeasureSupport;
import com.foggyframework.dataset.model.impl.measure.DbModelMeasureImpl;
import com.foggyframework.dataset.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.model.impl.preagg.PreAggregationImpl;
import com.foggyframework.dataset.model.impl.property.DbPropertyImpl;
import com.foggyframework.dataset.model.impl.utils.QueryObjectSupport;
import com.foggyframework.dataset.model.spi.*;
import com.foggyframework.dataset.utils.DbUtils;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptSourceClosureRevision;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Setter
@Getter
public class TableModelLoaderManagerImpl extends LoaderSupport implements TableModelLoaderManager {
    private static final int MAX_STALE_BUILD_ATTEMPTS = 3;

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

    Map<String, TableModelLoader> typeName2Loader = new HashMap<>();
    CatalogSnapshotStore catalogSnapshotStore;
    ModelBuildSingleFlight modelBuildSingleFlight;

    public TableModelLoaderManagerImpl(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader, List<DbModelLoadProcessor> processors, List<TableModelLoader> loaders) {
        this(systemBundlesContext, fileFsscriptLoader, processors, loaders, null,
                new DatasetProperties(), new CatalogSnapshotStore());
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
        this(systemBundlesContext, fileFsscriptLoader, processors, loaders, namedDataSourceResolver,
                datasetProperties, new CatalogSnapshotStore());
    }

    public TableModelLoaderManagerImpl(SystemBundlesContext systemBundlesContext,
                                       FileFsscriptLoader fileFsscriptLoader,
                                       List<DbModelLoadProcessor> processors,
                                       List<TableModelLoader> loaders,
                                       NamedDataSourceResolver namedDataSourceResolver,
                                       DatasetProperties datasetProperties,
                                       CatalogSnapshotStore catalogSnapshotStore) {
        super(systemBundlesContext, fileFsscriptLoader);
        this.processors = processors == null ? List.of() : List.copyOf(processors);
        this.datasetProperties = datasetProperties == null ? new DatasetProperties() : datasetProperties;
        this.namedDataSourceResolver = namedDataSourceResolver;
        this.catalogSnapshotStore = catalogSnapshotStore == null
                ? new CatalogSnapshotStore()
                : catalogSnapshotStore;
        this.modelBuildSingleFlight = new ModelBuildSingleFlight();
        Map<String, TableModelLoader> loaderRegistry = new HashMap<>();
        if (loaders != null) {
            loaders.forEach(loader -> loaderRegistry.put(loader.getTypeName(), loader));
        }
        this.typeName2Loader = Map.copyOf(loaderRegistry);
    }

    @Override
    public void clearAll() {
        catalogSnapshotStore.clearAll();
        log.debug("已清除所有命名空间的TableModel缓存");
    }

    @Override
    public void clearByNamespace(String namespace) {
        String normalizedNs = normalizeNamespace(namespace);
        int previousCount = catalogSnapshotStore.current(normalizedNs)
                .map(snapshot -> snapshot.tableModels().size())
                .orElse(0);
        catalogSnapshotStore.clearNamespace(normalizedNs);
        log.info("已清除命名空间 [{}] 的TableModel catalog，共 {} 个模型",
                normalizedNs.isEmpty() ? "默认" : normalizedNs, previousCount);
    }

    @Override
    public TableModel load(String name) {
        return load(name, null);
    }

    @Override
    public TableModel load(String modelName, String namespace) {
        String normalizedNamespace = normalizeNamespace(namespace);
        String canonicalModelName = requireCanonicalModelName(modelName);

        // A root flight builds before opening a candidate. Any cache miss while
        // another root candidate is already active therefore means a custom
        // builder introduced an undeclared dependency. Publishing that object
        // through a second flight would leak request-local staged state.
        CatalogCandidate activeCandidate = catalogSnapshotStore
                .currentCandidate(normalizedNamespace)
                .orElse(null);
        if (activeCandidate != null) {
            TableModel staged = activeCandidate.findTableModel(canonicalModelName);
            if (staged != null) {
                return staged;
            }
            throw new IllegalStateException(
                    "MODEL_BUILD_UNPREPARED_DEPENDENCY: table model "
                            + canonicalModelName);
        }

        RuntimeException lastStaleFailure = null;
        for (int attempt = 1; attempt <= MAX_STALE_BUILD_ATTEMPTS; attempt++) {
            TableModel current = findCurrentTableModel(normalizedNamespace, canonicalModelName);
            if (current != null) {
                return current;
            }

            CatalogBuildView buildView = catalogSnapshotStore.capture(normalizedNamespace);
            TableModel captured = findTableModel(buildView.baseSnapshot(), canonicalModelName);
            if (captured != null) {
                return captured;
            }
            PreparedTableModel prepared = prepareTableModel(
                    canonicalModelName, normalizedNamespace, buildView);
            try {
                return modelBuildSingleFlight.execute(
                        prepared.buildKey(),
                        () -> buildAndPublish(prepared));
            } catch (StaleCatalogBuildException | StaleDatasourceBindingException stale) {
                lastStaleFailure = stale;
            }
        }
        throw new IllegalStateException(
                "MODEL_BUILD_STALE_RETRY_EXHAUSTED: table model "
                        + canonicalModelName + " in namespace '" + normalizedNamespace + "'",
                lastStaleFailure);
    }

    private PreparedTableModel prepareTableModel(
            String modelName,
            String namespace,
            CatalogBuildView buildView
    ) {
        Fsscript fsscript = this.findFsscript(modelName, "tm", namespace);
        ExpEvaluator evaluator = fsscript.eval(systemBundlesContext.getApplicationContext());
        Object exported = evaluator.getExportObject("model");
        if (exported == null) {
            throw RX.throwAUserTip(DatasetMessages.modelNotFound(modelName));
        }
        Bundle bundle = fsscript.getFsscriptClosureDefinition()
                .getFsscriptClosureDefinitionSpace().getBundle();
        DbModelDef definition = FsscriptConversionService.getSharedInstance()
                .convert(exported, DbModelDef.class);
        applySemanticScalePolicy(definition, namespace);
        fix(definition);

        ResolvedDatasourceBinding binding = resolveDatasourceBinding(definition, namespace);
        definition.setDataSource(binding.dataSource());
        List<com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity>
                bindingIdentities = binding.identity() == null
                ? List.of()
                : List.of(binding.identity());
        boolean bindingIdentityComplete = binding.identity() != null && binding.cacheable();
        ModelBuildKey buildKey = ModelBuildKey.of(
                CatalogModelKey.table(modelName),
                namespace,
                buildView.catalogGeneration().orElse(null),
                buildView.sourceRevision(),
                bindingIdentities,
                bindingIdentityComplete);
        return new PreparedTableModel(
                modelName, namespace, buildView, fsscript, definition, bundle,
                binding, buildKey);
    }

    private TableModel buildAndPublish(PreparedTableModel prepared) {
        TableModel built = buildDetachedTableModel(prepared);

        try (CatalogSnapshotStore.CandidateScope scope =
                     catalogSnapshotStore.openCandidate(prepared.buildView())) {
            CatalogCandidate candidate = scope.candidate();
            TableModel existing = candidate.findTableModel(prepared.modelName());
            if (existing != null) {
                return existing;
            }
            stageBuiltTableModel(candidate, prepared, built);
            CatalogSnapshot published = commitIfBindingCurrent(prepared.binding(), scope);
            TableModel committed = published.tableModels().get(prepared.modelName());
            if (committed == null) {
                throw new IllegalStateException(
                        "committed catalog does not contain table model "
                                + prepared.modelName());
            }
            return committed;
        }
    }

    /**
     * Builds and stages a TM into an already-open refresh candidate without
     * publishing. The refresh coordinator remains the sole publisher.
     */
    public TableModel stageForRefresh(
            String modelName,
            String namespace,
            CatalogBuildView buildView,
            CatalogCandidate candidate
    ) {
        String canonicalNamespace = normalizeNamespace(namespace);
        String canonicalModelName = requireCanonicalModelName(modelName);
        if (candidate == null
                || !canonicalNamespace.equals(candidate.namespace())
                || !candidate.sourceRevision().equals(buildView.sourceRevision())) {
            throw new IllegalArgumentException(
                    "refresh TM stage does not match its candidate/build view");
        }
        TableModel existing = candidate.findTableModel(canonicalModelName);
        if (existing != null) {
            return existing;
        }
        PreparedTableModel prepared = prepareTableModel(
                canonicalModelName, canonicalNamespace, buildView);
        try {
            TableModel built = buildDetachedTableModel(prepared);
            stageBuiltTableModel(candidate, prepared, built);
            return built;
        } catch (Throwable failure) {
            candidate.fail("table model refresh build failed: " + canonicalModelName);
            throw ErrorUtils.toRuntimeException(failure);
        }
    }

    private TableModel buildDetachedTableModel(PreparedTableModel prepared) {
        TableModelLoader tableModelLoader = typeName2Loader.get(prepared.definition().getType());
        if (tableModelLoader == null) {
            String typeName = prepared.definition().getType();
            throw RX.throwAUserTip(DatasetMessages.loaderNotFound(
                    typeName, getLoaderDependencyHint(typeName)));
        }

        TableModel built = tableModelLoader.load(
                prepared.fsscript(), prepared.definition(), prepared.bundle());
        built = initialization(
                built,
                prepared.definition(),
                prepared.bundle(),
                prepared.binding().dataSource());
        ensureBindingStillCurrent(prepared.binding());
        return built;
    }

    private void stageBuiltTableModel(
            CatalogCandidate candidate,
            PreparedTableModel prepared,
            TableModel built
    ) {
        Map<String, com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity>
                bindings = prepared.binding().identity() == null
                ? Map.of()
                : Map.of(prepared.binding().identity().bindingKey(),
                prepared.binding().identity());
        candidate.putTableModel(
                prepared.modelName(),
                built,
                new ModelProvenance(
                        prepared.modelName(),
                        ModelProvenance.ModelKind.TABLE,
                        candidate.sourceRevision(),
                        Set.of(),
                        bindings,
                        prepared.binding().identity() != null
                                && prepared.binding().cacheable(),
                        List.of(),
                        modelSource(
                                prepared.bundle(),
                                prepared.namespace(),
                                prepared.fsscript())));
    }

    private ModelProvenance.ModelSource modelSource(
            Bundle bundle,
            String namespace,
            Fsscript fsscript
    ) {
        if (bundle == null || StringUtils.isEmpty(bundle.getName())
                || fsscript == null || StringUtils.isEmpty(fsscript.getPath())) {
            return null;
        }
        return new ModelProvenance.ModelSource(
                bundle.getName(),
                normalizeNamespace(namespace),
                fsscript.getPath(),
                FsscriptSourceClosureRevision.calculate(fsscript).orElse(null));
    }

    private void ensureBindingStillCurrent(ResolvedDatasourceBinding binding) {
        if (binding.identity() == null) {
            return;
        }
        if (namedDataSourceResolver == null) {
            throw new IllegalStateException(
                    "DATASOURCE_BINDING_CURRENTNESS_UNKNOWN: resolver unavailable");
        }
        BindingCurrentness currentness = namedDataSourceResolver.currentness(binding.identity());
        if (currentness == BindingCurrentness.STALE) {
            throw new StaleDatasourceBindingException(binding.identity().bindingKey());
        }
        if (currentness != BindingCurrentness.CURRENT) {
            throw new IllegalStateException(
                    "DATASOURCE_BINDING_CURRENTNESS_UNKNOWN: "
                            + binding.identity().bindingKey());
        }
    }

    private CatalogSnapshot commitIfBindingCurrent(
            ResolvedDatasourceBinding binding,
            CatalogSnapshotStore.CandidateScope scope
    ) {
        if (binding.identity() == null) {
            return scope.commit();
        }
        if (namedDataSourceResolver == null) {
            throw new IllegalStateException(
                    "DATASOURCE_BINDING_PUBLICATION_GUARD_UNAVAILABLE");
        }
        return namedDataSourceResolver.publishIfCurrent(
                List.of(binding.identity()), scope::commit);
    }

    private TableModel findCurrentTableModel(String namespace, String modelName) {
        return catalogSnapshotStore.readCurrent(namespace)
                .map(snapshot -> snapshot.tableModels().get(modelName))
                .orElse(null);
    }

    private TableModel findTableModel(CatalogSnapshot snapshot, String modelName) {
        return snapshot == null ? null : snapshot.tableModels().get(modelName);
    }

    private String requireCanonicalModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("canonical table model name must not be blank");
        }
        return modelName.trim();
    }

    private record PreparedTableModel(
            String modelName,
            String namespace,
            CatalogBuildView buildView,
            Fsscript fsscript,
            DbModelDef definition,
            Bundle bundle,
            ResolvedDatasourceBinding binding,
            ModelBuildKey buildKey
    ) {
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
     * <p>Priority: dataSourceName (resolved via NamedDataSourceResolver) > def.dataSource >
     * namespace default (resolved via NamedDataSourceResolver) > default dataSource
     *
     * @param def Model definition
     * @return Resolved DataSource
     */
    DataSource resolveDataSource(DbModelDef def, String namespace) {
        return resolveDatasourceBinding(def, namespace).dataSource();
    }

    private ResolvedDatasourceBinding resolveDatasourceBinding(DbModelDef def, String namespace) {
        String dataSourceName = def.getDataSourceName() == null ? null : def.getDataSourceName().trim();
        String normalizedNamespace = normalizeNamespace(namespace);

        // 1. An explicit data source name is an isolation boundary and must resolve exactly.
        if (StringUtils.isNotEmpty(dataSourceName)) {
            if (namedDataSourceResolver == null) {
                throw new IllegalStateException("Named data source resolver is not configured for model '"
                        + def.getName() + "', namespace '" + normalizedNamespace
                        + "', dataSourceName '" + dataSourceName + "'");
            }
            ResolvedDatasourceBinding namedBinding = namedDataSourceResolver.resolveBinding(dataSourceName);
            if (namedBinding != null) {
                log.debug("Using named data source: {} for model: {}", dataSourceName, def.getName());
                return namedBinding;
            }
            throw new IllegalArgumentException("Named data source '" + dataSourceName
                    + "' not found for model '" + def.getName()
                    + "' in namespace '" + normalizedNamespace + "'");
        }

        // 2. Use dataSource from definition
        if (def.getDataSource() != null) {
            return ResolvedDatasourceBinding.untracked(def.getDataSource());
        }

        // 3. Preserve the legacy resolver contract for an empty namespace:
        // resolveDefault historically received only named namespaces. A
        // process-local default therefore requires an explicit capability.
        if (namedDataSourceResolver != null) {
            ResolvedDatasourceBinding namespaceBinding = null;
            if (StringUtils.isNotEmpty(normalizedNamespace)) {
                namespaceBinding = namedDataSourceResolver.resolveDefaultBinding(normalizedNamespace);
            } else if (namedDataSourceResolver
                    instanceof ProcessLocalDefaultDataSourceResolver resolver) {
                namespaceBinding = resolver.resolveProcessLocalDefaultBinding();
            }
            if (namespaceBinding != null) {
                log.debug("Using namespace default data source for namespace: {}, model: {}",
                        normalizedNamespace, def.getName());
                return namespaceBinding;
            }
        }

        if (StringUtils.isNotEmpty(normalizedNamespace)) {
            if (allowGlobalDataSourceFallbackForNamespace()) {
                log.warn("Namespace '{}' has no default data source binding; explicit compatibility "
                                + "fallback is using the global data source for model '{}'",
                        normalizedNamespace, def.getName());
                return ResolvedDatasourceBinding.untracked(this.dataSource);
            }

            throw new IllegalStateException("No default data source bound for namespace '"
                    + normalizedNamespace + "' while loading model '" + def.getName() + "'");
        }

        // 4. Use default dataSource
        return ResolvedDatasourceBinding.untracked(this.dataSource);
    }

    private boolean allowGlobalDataSourceFallbackForNamespace() {
        return datasetProperties != null
                && datasetProperties.getDatasource() != null
                && datasetProperties.getDatasource().isAllowGlobalFallbackForNamespace();
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
        return initialization(jm, def, bundle, resolveDataSource(def, null));
    }

    TableModel initialization(
            TableModel jm,
            DbModelDef def,
            Bundle bundle,
            DataSource effectiveDataSource
    ) {
        RX.notNull(def, "加载模型时的def不得为空");
        // The caller that builds a catalog candidate supplies the generation-
        // pinned datasource. Never resolve dataSourceName a second time here:
        // a concurrent rebind could otherwise mix two generations in one TM.
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
        // SQL aliases are build-local state.  Global counters made the same TM
        // produce different SQL depending on which unrelated model loaded first.
        int dimensionIndex = 0;
        QueryObject qo = context.getJdbcModel().getQueryObject();
        qo.getDecorate(QueryObjectSupport.class).setAlias("m1");

        for (DbDimension dimension : context.getJdbcModel().getDimensions()) {
            QueryObject dqo = dimension.getQueryObject();
            if (dqo == null) {
                continue;
            }
            dqo.getDecorate(QueryObjectSupport.class).setAlias("d" + (++dimensionIndex));
            if (dimension.getDecorate(DbModelParentChildDimensionImpl.class) != null) {
                DbModelParentChildDimensionImpl pcDim = dimension.getDecorate(DbModelParentChildDimensionImpl.class);
                // 为闭包表分配别名（后代方向）
                pcDim.getClosureQueryObject().getDecorate(QueryObjectSupport.class)
                        .setAlias("d" + (++dimensionIndex));
                // 为闭包表分配别名（祖先方向）
                if (pcDim.getAncestorClosureQueryObject() != null) {
                    pcDim.getAncestorClosureQueryObject().getDecorate(QueryObjectSupport.class)
                            .setAlias("d" + (++dimensionIndex));
                }
                // 为层级视角维度表分配别名
                if (pcDim.getHierarchyQueryObject() != null) {
                    pcDim.getHierarchyQueryObject().getDecorate(QueryObjectSupport.class)
                            .setAlias("d" + (++dimensionIndex));
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
        ResolvedFormula formula = resolveDialectFormula(ds, captionDef.getFormulaDef(), captionDef.getDialectFormulaDef());
        if (formula.builder != null) {
            dimension.setCaptionFormulaBuilder(formula.builder);
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
     * @return 解析后的公式定义，或空定义
     */
    private ResolvedFormula resolveDialectFormula(DataSource ds, DbFormulaDef formulaDef, java.util.Map<String, DbFormulaDef> dialectFormulaDef) {
        // 1. 尝试方言专属公式
        if (dialectFormulaDef != null && !dialectFormulaDef.isEmpty()) {
            DbType dbType = DbUtils.getDialect(ds).getDbType();
            String dbTypeKey = dbType.name().toLowerCase(); // postgresql, mysql, sqlserver, sqlite, oracle
            DbFormulaDef dialectFormula = dialectFormulaDef.get(dbTypeKey);
            ResolvedFormula resolved = resolveFormulaDef(dialectFormula);
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        // 2. 回退到通用公式
        return resolveFormulaDef(formulaDef);
    }

    private ResolvedFormula resolveFormulaDef(DbFormulaDef formulaDef) {
        if (formulaDef == null) {
            return ResolvedFormula.empty();
        }
        if (formulaDef.getBuilder() != null) {
            return new ResolvedFormula(formulaDef.getBuilder(), null);
        }
        if (StringUtils.isNotEmpty(formulaDef.getValue())) {
            return new ResolvedFormula(null, formulaDef.getValue());
        }
        return ResolvedFormula.empty();
    }

    private static class ResolvedFormula {
        private static final ResolvedFormula EMPTY = new ResolvedFormula(null, null);

        private final FsscriptFunction builder;
        private final String sql;

        private ResolvedFormula(FsscriptFunction builder, String sql) {
            this.builder = builder;
            this.sql = sql;
        }

        private static ResolvedFormula empty() {
            return EMPTY;
        }

        private boolean isPresent() {
            return builder != null || StringUtils.isNotEmpty(sql);
        }
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
        validatePropertyColumnContract(context, dimension, propertyDef, property);
        property.validateSemanticScaleContract(propertyDef.getFormulaDef(), propertyDef.getDialectFormulaDef());
        property.init();
        if (property.getDictionaryDiscovery() != null) {
            property.getDictionaryDiscovery().validate("property " + context.getJdbcModel().getName() + "." + property.getName());
        }

        processJdbcDataProvider(property.getDataProvider());

        DbProperty dbProperty = property;

        // 解析方言公式：dialectFormulaDef[dbType] > formulaDef > 无公式
        DataSource propDs = context.getDataSource();
        ResolvedFormula propFormula = resolveDialectFormula(propDs, propertyDef.getFormulaDef(), propertyDef.getDialectFormulaDef());
        if (propFormula.builder != null) {
            dbProperty.setFormulaBuilder(propFormula.builder);
        }
        if (StringUtils.isNotEmpty(propFormula.sql)) {
            property.setFormulaSql(propFormula.sql);
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

    private void validatePropertyColumnContract(JdbcModelLoadContext context,
                                                DbDimensionSupport dimension,
                                                DbPropertyDef propertyDef,
                                                DbPropertyImpl property) {
        if (StringUtils.isNotEmpty(property.getColumn())) {
            return;
        }
        String path = buildPropertyPath(context, dimension, propertyDef, property);
        String message = path + " column不能为空";
        if (hasFormula(propertyDef.getFormulaDef(), propertyDef.getDialectFormulaDef())) {
            message += "；formulaDef/dialectFormulaDef 字段必须声明 carrier column，用于字段元数据、权限和物理列绑定";
        }
        throw RX.throwAUserTip(message);
    }

    private String buildPropertyPath(JdbcModelLoadContext context,
                                     DbDimensionSupport dimension,
                                     DbPropertyDef propertyDef,
                                     DbPropertyImpl property) {
        String modelName = context.getJdbcModel().getName();
        if (StringUtils.isEmpty(modelName)) {
            modelName = "<unknown-model>";
        }
        String propertyName = firstNotEmpty(propertyDef.getName(), property.getName(),
                propertyDef.getAlias(), property.getAlias(), propertyDef.getColumn(), "<unnamed-property>");
        if (dimension == null || StringUtils.isEmpty(dimension.getName())) {
            return modelName + "." + propertyName;
        }
        return modelName + "." + dimension.getName() + "." + propertyName;
    }

    private boolean hasFormula(DbFormulaDef formulaDef, Map<String, DbFormulaDef> dialectFormulaDef) {
        return formulaDef != null || (dialectFormulaDef != null && !dialectFormulaDef.isEmpty());
    }

    private String firstNotEmpty(String... values) {
        for (String value : values) {
            if (StringUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return "";
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
        ResolvedFormula measureFormula = resolveDialectFormula(measureDs, measureDef.getFormulaDef(), measureDef.getDialectFormulaDef());
        if (measureFormula.builder != null) {
            measure.getDecorate(DbMeasureSupport.class).setFormulaBuilder(measureFormula.builder);
        }
        if (StringUtils.isNotEmpty(measureFormula.sql)) {
            measure.getDecorate(DbMeasureSupport.class).setFormulaSql(measureFormula.sql);
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
