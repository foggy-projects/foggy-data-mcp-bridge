package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.conversion.FsscriptConversionService;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.tuple.Tuple2;
import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.access.DbAccessDef;
import com.foggyframework.dataset.db.model.semantic.member.permission.QmMemberPermissionDef;
import com.foggyframework.dataset.db.model.def.column.DbColumnGroupDef;
import com.foggyframework.dataset.db.model.def.order.OrderDef;
import com.foggyframework.dataset.db.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.db.model.def.query.QueryConditionDef;
import com.foggyframework.dataset.db.model.def.query.SelectColumnDef;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.WindowOrderDef;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.LoaderSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.query.*;
import com.foggyframework.dataset.db.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogBuildView;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.db.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.db.model.lifecycle.catalog.StaleCatalogBuildException;
import com.foggyframework.dataset.db.model.lifecycle.concurrent.ModelBuildKey;
import com.foggyframework.dataset.db.model.lifecycle.concurrent.ModelBuildSingleFlight;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.db.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.db.model.lifecycle.port.DatasourceBindingResolver;
import com.foggyframework.dataset.db.model.lifecycle.port.StaleDatasourceBindingException;
import com.foggyframework.dataset.db.model.proxy.ColumnRef;
import com.foggyframework.dataset.db.model.proxy.DimensionProxy;
import com.foggyframework.dataset.db.model.proxy.JoinBuilder;
import com.foggyframework.dataset.db.model.proxy.TableModelProxy;
import com.foggyframework.dataset.db.model.semantic.member.SyntheticMemberQueryModelDescriptor;
import com.foggyframework.dataset.db.model.semantic.member.SyntheticMemberQueryModelFactory;
import com.foggyframework.dataset.db.model.semantic.member.SyntheticMemberQueryModelResolver;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.support.QueryColumnGroup;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import javax.sql.DataSource;
import java.util.*;

@Slf4j
@Setter
@Getter
public class QueryModelLoaderImpl extends LoaderSupport implements QueryModelLoader {

    private static final int MAX_STALE_BUILD_ATTEMPTS = 3;

    private TableModelLoaderManager tableModelLoaderManager;

    @Resource
    private DataSource defaultDataSource;

    private DbModelFileChangeHandler fileChangeHandler;

    private List<QueryModelBuilder> queryModelBuilders;

    private CatalogSnapshotStore catalogSnapshotStore;

    private ModelBuildSingleFlight modelBuildSingleFlight;

    @Resource
    private SyntheticMemberQueryModelFactory syntheticMemberQueryModelFactory;

    private final SyntheticMemberQueryModelResolver syntheticMemberQueryModelResolver = new SyntheticMemberQueryModelResolver();
    public QueryModelLoaderImpl(TableModelLoaderManager tableModelLoaderManager,
                                SystemBundlesContext systemBundlesContext,
                                FileFsscriptLoader fileFsscriptLoader,
                                List<QueryModelBuilder> queryModelBuilders) {
        this(tableModelLoaderManager, systemBundlesContext, fileFsscriptLoader, queryModelBuilders,
                tableModelLoaderManager instanceof com.foggyframework.dataset.db.model.impl.loader.TableModelLoaderManagerImpl manager
                        ? manager.getCatalogSnapshotStore()
                        : new CatalogSnapshotStore());
    }

    public QueryModelLoaderImpl(TableModelLoaderManager tableModelLoaderManager,
                                SystemBundlesContext systemBundlesContext,
                                FileFsscriptLoader fileFsscriptLoader,
                                List<QueryModelBuilder> queryModelBuilders,
                                CatalogSnapshotStore catalogSnapshotStore) {
        super(systemBundlesContext, fileFsscriptLoader);
        this.tableModelLoaderManager = tableModelLoaderManager;
        this.queryModelBuilders = queryModelBuilders == null ? List.of() : List.copyOf(queryModelBuilders);
        this.catalogSnapshotStore = Objects.requireNonNull(catalogSnapshotStore, "catalogSnapshotStore");
        this.modelBuildSingleFlight = tableModelLoaderManager instanceof TableModelLoaderManagerImpl manager
                ? manager.getModelBuildSingleFlight()
                : new ModelBuildSingleFlight();
    }

    @Override
    public void clearAll() {
        catalogSnapshotStore.clearAll();
        log.debug("已清除所有命名空间的QueryModel缓存");
    }

    @Override
    public void clearByNamespace(String namespace) {
        String normalizedNs = normalizeNamespace(namespace);
        int previousCount = catalogSnapshotStore.current(normalizedNs)
                .map(snapshot -> snapshot.queryModels().size() + snapshot.syntheticQueryModels().size())
                .orElse(0);
        catalogSnapshotStore.clearNamespace(normalizedNs);
        log.info("已清除命名空间 [{}] 的QueryModel catalog，包含 {} 个模型",
                normalizedNs.isEmpty() ? "默认" : normalizedNs, previousCount);
    }

    /**
     * 标准化命名空间（null或空字符串都视为默认命名空间）
     */
    private String normalizeNamespace(String namespace) {
        return (namespace == null || namespace.trim().isEmpty()) ? "" : namespace.trim();
    }

    /**
     * 在执行查询前，我们需要先获取查询模型
     *
     * <p>支持通过模型全名或简称查询（从指定命名空间）
     *
     * @param queryModelNameOrAlias 模型名称或简称
     * @param namespace             命名空间（null或空字符串表示默认命名空间）
     * @return 查询模型
     */
    @Override
    public QueryModel getJdbcQueryModel(String queryModelNameOrAlias, String namespace) {
        return resolveJdbcQueryModel(queryModelNameOrAlias, namespace).model();
    }

    @Override
    public CatalogResolution<QueryModel> resolveJdbcQueryModel(
            String queryModelNameOrAlias,
            String namespace
    ) {
        String normalizedNs = normalizeNamespace(namespace);
        try (NamespaceScope ignored = NamespaceContext.open(normalizedNs)) {
            return resolveWithSingleFlight(queryModelNameOrAlias, normalizedNs, null);
        }
    }

    private CatalogResolution<QueryModel> resolveWithSingleFlight(
            String requestedNameOrAlias,
            String namespace,
            BundleResource explicitResource
    ) {
        String requested = requireQueryModelName(requestedNameOrAlias);
        RuntimeException lastStaleFailure = null;
        for (int attempt = 1; attempt <= MAX_STALE_BUILD_ATTEMPTS; attempt++) {
            CatalogSnapshot active = catalogSnapshotStore.readCurrent(namespace).orElse(null);
            if (active != null && active.resolveQueryModel(requested).isPresent()) {
                return resolution(active, requested);
            }

            SourceRevision sourceAtPrepareStart =
                    catalogSnapshotStore.currentSourceRevision(namespace);
            Set<String> discovery = discoverQueryModelNames(namespace);
            Fsscript explicitFsscript = null;
            String canonicalName;
            if (explicitResource == null) {
                canonicalName = canonicalizeQueryModelName(
                        requested, namespace, discovery);
            } else {
                // Preserve the legacy observable order: script acquisition is
                // attempted before filename validation.
                explicitFsscript = fileFsscriptLoader.findLoadFsscript(explicitResource);
                canonicalName = canonicalResourceModelName(explicitResource);
            }
            try {
                PreparedQueryModel prepared;
                if (isSyntheticName(canonicalName)) {
                    prepared = prepareSyntheticQueryModel(
                            canonicalName, namespace, discovery, sourceAtPrepareStart);
                } else {
                    prepared = prepareQueryModel(
                            canonicalName,
                            namespace,
                            discovery,
                            sourceAtPrepareStart,
                            explicitFsscript);
                }
                return modelBuildSingleFlight.execute(
                        prepared.buildKey(),
                        () -> buildAndPublish(prepared));
            } catch (StaleCatalogBuildException | StaleDatasourceBindingException stale) {
                lastStaleFailure = stale;
            }
        }
        throw new IllegalStateException(
                "MODEL_BUILD_STALE_RETRY_EXHAUSTED: query model " + requested
                        + " in namespace '" + namespace + "'",
                lastStaleFailure);
    }

    private PreparedQueryModel prepareQueryModel(
            String canonicalName,
            String namespace,
            Set<String> discovery,
            SourceRevision sourceAtPrepareStart,
            Fsscript explicitFsscript
    ) {
        Fsscript fsscript = explicitFsscript == null
                ? findFsscriptWithNamespace(canonicalName, namespace, "qm")
                : explicitFsscript;
        ExpEvaluator evaluator = evalQmScript(fsscript);
        Object exported = evaluator.getExportObject("queryModel");
        DbQueryModelDef definition = FsscriptConversionService.getSharedInstance()
                .convert(exported, DbQueryModelDef.class);

        Set<String> tableDependencies = tableDependencies(definition);
        if (tableModelLoaderManager != null) {
            for (String tableDependency : tableDependencies) {
                tableModelLoaderManager.load(tableDependency, namespace);
            }
        }

        CatalogBuildView buildView = catalogSnapshotStore.capture(namespace);
        ensureSourceDidNotChange(namespace, sourceAtPrepareStart, buildView);
        DependencyIdentity dependencyIdentity = tableDependencyIdentity(
                buildView.baseSnapshot(), tableDependencies);
        ModelBuildKey buildKey = ModelBuildKey.of(
                CatalogModelKey.query(canonicalName),
                namespace,
                buildView.catalogGeneration().orElse(null),
                buildView.sourceRevision(),
                dependencyIdentity.bindings().values(),
                dependencyIdentity.complete());
        return new PreparedQueryModel(
                canonicalName,
                namespace,
                discovery,
                buildView,
                false,
                fsscript,
                evaluator,
                definition,
                null,
                null,
                dependencyIdentity.dependencies(),
                dependencyIdentity.bindings(),
                dependencyIdentity.complete(),
                buildKey);
    }

    private PreparedQueryModel prepareSyntheticQueryModel(
            String canonicalName,
            String namespace,
            Set<String> discovery,
            SourceRevision sourceAtPrepareStart
    ) {
        int separator = canonicalName.indexOf(
                SyntheticMemberQueryModelResolver.MODEL_SEPARATOR);
        if (separator <= 0 || separator == canonicalName.length() - 1) {
            throw new IllegalArgumentException(
                    "invalid synthetic query model name: " + canonicalName);
        }
        String sourceName = canonicalName.substring(0, separator);
        String selector = canonicalName.substring(separator + 1);
        resolveJdbcQueryModel(sourceName, namespace);

        CatalogBuildView buildView = catalogSnapshotStore.capture(namespace);
        ensureSourceDidNotChange(namespace, sourceAtPrepareStart, buildView);
        CatalogSnapshot base = buildView.baseSnapshot();
        if (base == null) {
            throw new IllegalStateException(
                    "synthetic source catalog is absent: " + sourceName);
        }
        ModelProvenance sourceProvenance = base.queryModelProvenance(sourceName)
                .orElseThrow(() -> new IllegalStateException(
                        "synthetic source provenance is absent: " + sourceName));
        CatalogModelKey sourceKey = sourceProvenance.key();
        Map<String, DatasourceBindingIdentity> bindings =
                new TreeMap<>(sourceProvenance.datasourceBindings());
        boolean complete = sourceProvenance.bindingIdentityComplete();
        ModelBuildKey buildKey = ModelBuildKey.of(
                CatalogModelKey.syntheticQuery(canonicalName),
                namespace,
                buildView.catalogGeneration().orElse(null),
                buildView.sourceRevision(),
                bindings.values(),
                complete);
        return new PreparedQueryModel(
                canonicalName,
                namespace,
                discovery,
                buildView,
                true,
                null,
                null,
                null,
                sourceName,
                selector,
                Set.of(sourceKey),
                Collections.unmodifiableMap(bindings),
                complete,
                buildKey);
    }

    private CatalogResolution<QueryModel> buildAndPublish(PreparedQueryModel prepared) {
        try (CatalogSnapshotStore.CandidateScope scope =
                     catalogSnapshotStore.openCandidate(prepared.buildView())) {
            if (!scope.isOwner()) {
                throw new IllegalStateException(
                        "query root unexpectedly joined another catalog candidate");
            }
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(prepared.discovery());
            QueryModel existing = candidate.findQueryModel(prepared.canonicalName());
            if (existing != null) {
                CatalogSnapshot base = prepared.buildView().baseSnapshot();
                if (base == null) {
                    throw new IllegalStateException(
                            "candidate contains a query model absent from its base snapshot");
                }
                return resolution(base, prepared.canonicalName());
            }

            CatalogSnapshot published;
            try {
                QueryModelSupport built;
                if (prepared.synthetic()) {
                    QueryModel sourceModel = candidate.findQueryModel(prepared.sourceModelName());
                    if (sourceModel == null) {
                        throw new IllegalStateException(
                                "synthetic source model is absent: "
                                        + prepared.sourceModelName());
                    }
                    SyntheticMemberQueryModelDescriptor descriptor =
                            syntheticMemberQueryModelResolver.resolve(
                                    sourceModel,
                                    prepared.syntheticSelector(),
                                    prepared.namespace());
                    QueryModel synthetic = syntheticMemberQueryModelFactory.build(
                            sourceModel, descriptor);
                    if (!(synthetic instanceof QueryModelSupport support)) {
                        throw new IllegalStateException(
                                "synthetic query model must extend QueryModelSupport");
                    }
                    built = support;
                    stageQueryModel(
                            candidate,
                            prepared.canonicalName(),
                            built,
                            true,
                            Set.of(prepared.sourceModelName()));
                } else {
                    built = loadJdbcQueryModel(
                            prepared.evaluator(),
                            prepared.fsscript(),
                            prepared.definition());
                    if (built.getName() != null
                            && !prepared.canonicalName().equals(built.getName())) {
                        throw new IllegalStateException("QM resource name '"
                                + prepared.canonicalName()
                                + "' does not match exported canonical name '"
                                + built.getName() + "'");
                    }
                    stageQueryModel(
                            candidate,
                            prepared.canonicalName(),
                            built,
                            false,
                            Set.of());
                }
                verifyPreparedProvenance(candidate, prepared);
                Map<String, DatasourceBindingIdentity> effectiveBindings =
                        candidate.effectiveDatasourceBindings();
                ensureBindingsStillCurrent(effectiveBindings);
                published = commitIfBindingsCurrent(
                        scope, effectiveBindings.values());
            } catch (Throwable failure) {
                candidate.fail("query model build failed");
                throw ErrorUtils.toRuntimeException(failure);
            }
            return resolution(published, prepared.canonicalName());
        }
    }

    /**
     * Builds and stages one canonical QM in an already-open refresh candidate.
     * This method never commits; the namespace refresh coordinator owns the
     * single atomic publication.
     */
    public QueryModel stageForRefresh(
            String canonicalName,
            String namespace,
            Set<String> discovery,
            CatalogBuildView buildView,
            CatalogCandidate candidate
    ) {
        String canonicalNamespace = normalizeNamespace(namespace);
        String modelName = requireQueryModelName(canonicalName);
        if (candidate == null
                || !canonicalNamespace.equals(candidate.namespace())
                || !candidate.sourceRevision().equals(buildView.sourceRevision())) {
            throw new IllegalArgumentException(
                    "refresh QM stage does not match its candidate/build view");
        }
        candidate.discoverQueryModels(discovery);
        QueryModel existing = candidate.findQueryModel(modelName);
        if (existing != null) {
            return existing;
        }

        try {
            QueryModelSupport built;
            if (isSyntheticName(modelName)) {
                int separator = modelName.indexOf(
                        SyntheticMemberQueryModelResolver.MODEL_SEPARATOR);
                String sourceName = modelName.substring(0, separator);
                String selector = modelName.substring(separator + 1);
                QueryModel sourceModel = candidate.findQueryModel(sourceName);
                if (sourceModel == null) {
                    throw new IllegalStateException(
                            "synthetic source model is absent: " + sourceName);
                }
                SyntheticMemberQueryModelDescriptor descriptor =
                        syntheticMemberQueryModelResolver.resolve(
                                sourceModel, selector, canonicalNamespace);
                QueryModel synthetic = syntheticMemberQueryModelFactory.build(
                        sourceModel, descriptor);
                if (!(synthetic instanceof QueryModelSupport support)) {
                    throw new IllegalStateException(
                            "synthetic query model must extend QueryModelSupport");
                }
                built = support;
                stageQueryModel(candidate, modelName, built, true, Set.of(sourceName));
                return built;
            }

            Fsscript fsscript = findFsscriptWithNamespace(
                    modelName, canonicalNamespace, "qm");
            ExpEvaluator evaluator = evalQmScript(fsscript);
            Object exported = evaluator.getExportObject("queryModel");
            DbQueryModelDef definition = FsscriptConversionService.getSharedInstance()
                    .convert(exported, DbQueryModelDef.class);
            Set<String> tableDependencies = tableDependencies(definition);
            for (String tableDependency : tableDependencies) {
                if (tableModelLoaderManager instanceof TableModelLoaderManagerImpl manager) {
                    manager.stageForRefresh(
                            tableDependency,
                            canonicalNamespace,
                            buildView,
                            candidate);
                } else if (tableModelLoaderManager != null) {
                    tableModelLoaderManager.load(tableDependency, canonicalNamespace);
                }
            }

            built = loadJdbcQueryModel(evaluator, fsscript, definition);
            if (built.getName() != null && !modelName.equals(built.getName())) {
                throw new IllegalStateException("QM resource name '" + modelName
                        + "' does not match exported canonical name '"
                        + built.getName() + "'");
            }
            stageQueryModel(candidate, modelName, built, false, Set.of());
            return built;
        } catch (Throwable failure) {
            candidate.fail("query model refresh build failed: " + modelName);
            throw ErrorUtils.toRuntimeException(failure);
        }
    }

    private CatalogSnapshot commitIfBindingsCurrent(
            CatalogSnapshotStore.CandidateScope scope,
            Collection<DatasourceBindingIdentity> bindings
    ) {
        if (bindings.isEmpty()) {
            return scope.commit();
        }
        DatasourceBindingResolver resolver = bindingResolver();
        if (resolver == null) {
            throw new IllegalStateException(
                    "DATASOURCE_BINDING_PUBLICATION_GUARD_UNAVAILABLE");
        }
        return resolver.publishIfCurrent(bindings, scope::commit);
    }

    private void verifyPreparedProvenance(
            CatalogCandidate candidate,
            PreparedQueryModel prepared
    ) {
        ModelProvenance.ModelKind kind = prepared.synthetic()
                ? ModelProvenance.ModelKind.SYNTHETIC_QUERY
                : ModelProvenance.ModelKind.QUERY;
        ModelProvenance actual = candidate.modelProvenance(
                kind, prepared.canonicalName());
        if (actual == null
                || actual.bindingIdentityComplete()
                != prepared.bindingIdentityComplete()
                || !actual.datasourceBindings().equals(prepared.bindings())
                || !actual.modelDependencies().equals(prepared.dependencies())) {
            throw new IllegalStateException(
                    "MODEL_BUILD_KEY_PROVENANCE_MISMATCH: "
                            + prepared.canonicalName());
        }
    }

    private void ensureBindingsStillCurrent(
            Map<String, DatasourceBindingIdentity> bindings
    ) {
        if (bindings.isEmpty()) {
            return;
        }
        DatasourceBindingResolver resolver = bindingResolver();
        if (resolver == null) {
            throw new IllegalStateException(
                    "DATASOURCE_BINDING_CURRENTNESS_UNKNOWN: resolver unavailable");
        }
        for (DatasourceBindingIdentity binding : bindings.values()) {
            BindingCurrentness currentness = resolver.currentness(binding);
            if (currentness == BindingCurrentness.STALE) {
                throw new StaleDatasourceBindingException(binding.bindingKey());
            }
            if (currentness != BindingCurrentness.CURRENT) {
                throw new IllegalStateException(
                        "DATASOURCE_BINDING_CURRENTNESS_UNKNOWN: "
                                + binding.bindingKey());
            }
        }
    }

    private DatasourceBindingResolver bindingResolver() {
        return tableModelLoaderManager instanceof TableModelLoaderManagerImpl manager
                ? manager.getNamedDataSourceResolver()
                : null;
    }

    private DependencyIdentity tableDependencyIdentity(
            CatalogSnapshot snapshot,
            Set<String> tableDependencies
    ) {
        LinkedHashSet<CatalogModelKey> dependencies = new LinkedHashSet<>();
        TreeMap<String, DatasourceBindingIdentity> bindings = new TreeMap<>();
        boolean complete = true;
        for (String dependencyName : tableDependencies) {
            CatalogModelKey key = CatalogModelKey.table(dependencyName);
            dependencies.add(key);
            ModelProvenance provenance = snapshot == null
                    ? null
                    : snapshot.provenance().get(key);
            if (provenance == null) {
                complete = false;
                continue;
            }
            complete &= provenance.bindingIdentityComplete();
            mergeBindings(bindings, provenance.datasourceBindings());
        }
        return new DependencyIdentity(
                Collections.unmodifiableSet(dependencies),
                Collections.unmodifiableMap(bindings),
                complete);
    }

    private void mergeBindings(
            Map<String, DatasourceBindingIdentity> target,
            Map<String, DatasourceBindingIdentity> additions
    ) {
        for (Map.Entry<String, DatasourceBindingIdentity> entry : additions.entrySet()) {
            DatasourceBindingIdentity previous = target.putIfAbsent(
                    entry.getKey(), entry.getValue());
            if (previous != null && !previous.equals(entry.getValue())) {
                throw RX.throwAUserTip(
                        "不同 datasource binding generation 的 TM 不能配置在同一 QM 中");
            }
        }
    }

    private Set<String> tableDependencies(DbQueryModelDef definition) {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        if (definition == null) {
            return Collections.unmodifiableSet(dependencies);
        }
        addTableDependency(dependencies, definition.getModel());
        if (definition.getJoins() != null) {
            for (Object join : definition.getJoins()) {
                if (join instanceof JoinBuilder joinBuilder) {
                    addTableDependency(dependencies, joinBuilder.getLeft());
                    addTableDependency(dependencies, joinBuilder.getRight());
                }
            }
        }
        return Collections.unmodifiableSet(dependencies);
    }

    private void addTableDependency(
            Set<String> dependencies,
            TableModelProxy proxy
    ) {
        if (proxy != null
                && proxy.getModelName() != null
                && !proxy.getModelName().isBlank()) {
            dependencies.add(proxy.getModelName().trim());
        }
    }

    private String canonicalizeQueryModelName(
            String requested,
            String namespace,
            Set<String> discovery
    ) {
        CatalogSnapshot active = catalogSnapshotStore.readCurrent(namespace).orElse(null);
        if (active != null && active.resolveQueryModel(requested).isPresent()) {
            return active.canonicalQueryModelName(requested);
        }
        int separator = requested.indexOf(
                SyntheticMemberQueryModelResolver.MODEL_SEPARATOR);
        String sourceOrName = separator < 0
                ? requested
                : requested.substring(0, separator);
        String suffix = separator < 0 ? "" : requested.substring(separator);
        CatalogBuildView view = catalogSnapshotStore.capture(namespace);
        try (CatalogSnapshotStore.CandidateScope scope =
                     catalogSnapshotStore.openCandidate(view)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(discovery);
            return candidate.resolveCanonicalName(sourceOrName) + suffix;
        }
    }

    private void ensureSourceDidNotChange(
            String namespace,
            SourceRevision expected,
            CatalogBuildView actual
    ) {
        if (!expected.equals(actual.sourceRevision())) {
            throw new StaleCatalogBuildException(
                    namespace,
                    StaleCatalogBuildException.Reason.SOURCE_REVISION_CHANGED);
        }
    }

    private boolean isSyntheticName(String canonicalName) {
        return canonicalName.indexOf(
                SyntheticMemberQueryModelResolver.MODEL_SEPARATOR) > 0;
    }

    private String requireQueryModelName(String requested) {
        if (requested == null || requested.isBlank()) {
            throw new IllegalArgumentException(
                    "query model name or alias must not be blank");
        }
        return requested.trim();
    }

    private record DependencyIdentity(
            Set<CatalogModelKey> dependencies,
            Map<String, DatasourceBindingIdentity> bindings,
            boolean complete
    ) {
    }

    private record PreparedQueryModel(
            String canonicalName,
            String namespace,
            Set<String> discovery,
            CatalogBuildView buildView,
            boolean synthetic,
            Fsscript fsscript,
            ExpEvaluator evaluator,
            DbQueryModelDef definition,
            String sourceModelName,
            String syntheticSelector,
            Set<CatalogModelKey> dependencies,
            Map<String, DatasourceBindingIdentity> bindings,
            boolean bindingIdentityComplete,
            ModelBuildKey buildKey
    ) {
    }

    @Override
    public Map<String, CatalogResolution<QueryModel>> resolveJdbcQueryModels(
            Collection<String> queryModelNames,
            String namespace
    ) {
        if (queryModelNames == null || queryModelNames.isEmpty()) {
            return Map.of();
        }
        String normalizedNs = normalizeNamespace(namespace);
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        queryModelNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .forEach(requested::add);
        for (String name : requested) {
            resolveJdbcQueryModel(name, normalizedNs);
        }

        CatalogSnapshot pinned = catalogSnapshotStore.readCurrent(normalizedNs)
                .orElseThrow(() -> new IllegalStateException(
                        "catalog disappeared before metadata snapshot capture"));
        LinkedHashMap<String, CatalogResolution<QueryModel>> resolutions = new LinkedHashMap<>();
        for (String name : requested) {
            resolutions.put(name, resolution(pinned, name));
        }
        return Collections.unmodifiableMap(resolutions);
    }

    /**
     * 在指定命名空间中查找FSScript
     */
    private Fsscript findFsscriptWithNamespace(String modelName, String namespace, String suffix) {
        String fileName = modelName + "." + suffix;
        BundleResource resource = systemBundlesContext.findResourceByName(fileName, namespace, true);
        return fileFsscriptLoader.findLoadFsscript(resource);
    }

    private void stageQueryModel(
            CatalogCandidate candidate,
            String canonicalName,
            QueryModelSupport model,
            boolean synthetic,
            Set<String> additionalDependencies
    ) {
        model.setShortAlias(candidate.aliasFor(canonicalName));
        LinkedHashSet<CatalogModelKey> dependencies = new LinkedHashSet<>();
        for (String queryDependency : additionalDependencies) {
            CatalogModelKey queryKey = CatalogModelKey.query(queryDependency);
            if (candidate.modelProvenance(queryKey) == null) {
                queryKey = CatalogModelKey.syntheticQuery(queryDependency);
            }
            dependencies.add(queryKey);
        }
        if (model.getJdbcModelList() != null) {
            for (TableModel tableModel : model.getJdbcModelList()) {
                if (tableModel != null && tableModel.getName() != null && !tableModel.getName().isBlank()) {
                    CatalogModelKey tableKey = CatalogModelKey.table(tableModel.getName());
                    if (!synthetic) {
                        ModelProvenance stagedProvenance = candidate.modelProvenance(tableKey);
                        boolean externalManager = !(tableModelLoaderManager instanceof
                                com.foggyframework.dataset.db.model.impl.loader.TableModelLoaderManagerImpl);
                        if (stagedProvenance == null && externalManager) {
                            candidate.putTableModel(
                                    tableModel.getName(),
                                    tableModel,
                                    new ModelProvenance(
                                            tableModel.getName(),
                                            ModelProvenance.ModelKind.TABLE,
                                            candidate.sourceRevision(),
                                            Set.of(),
                                            Map.of(),
                                            false,
                                            List.of("external table model binding identity unavailable")));
                            stagedProvenance = candidate.modelProvenance(tableKey);
                        }
                        if (externalManager
                                && stagedProvenance != null
                                && candidate.findTableModel(tableModel.getName()) != tableModel) {
                            throw new IllegalStateException(
                                    "query model references a different table model instance for "
                                            + tableModel.getName());
                        }
                    }
                    // A synthetic member QM may expose a request-local derived
                    // table wrapper that is not itself a catalog slot. Its
                    // canonical source QM below already carries the complete
                    // transitive TM/binding provenance.
                    if (!synthetic || candidate.modelProvenance(tableKey) != null) {
                        dependencies.add(tableKey);
                    }
                }
            }
        }

        Map<String, DatasourceBindingIdentity> bindings = new TreeMap<>();
        boolean complete = true;
        for (CatalogModelKey dependency : dependencies) {
            ModelProvenance dependencyProvenance = candidate.modelProvenance(dependency);
            if (dependencyProvenance == null) {
                complete = false;
                continue;
            }
            complete &= dependencyProvenance.bindingIdentityComplete();
            for (Map.Entry<String, DatasourceBindingIdentity> binding
                    : dependencyProvenance.datasourceBindings().entrySet()) {
                DatasourceBindingIdentity previous = bindings.putIfAbsent(
                        binding.getKey(), binding.getValue());
                if (previous != null && !previous.equals(binding.getValue())) {
                    throw RX.throwAUserTip("不同 datasource binding generation 的 TM 不能配置在同一 QM 中");
                }
            }
        }

        ModelProvenance provenance = new ModelProvenance(
                canonicalName,
                synthetic ? ModelProvenance.ModelKind.SYNTHETIC_QUERY : ModelProvenance.ModelKind.QUERY,
                candidate.sourceRevision(),
                dependencies,
                bindings,
                complete,
                List.of()
        );
        if (synthetic) {
            candidate.putSyntheticQueryModel(canonicalName, model, provenance);
        } else {
            candidate.putQueryModel(canonicalName, model, provenance);
        }
    }

    private CatalogResolution<QueryModel> resolution(
            CatalogSnapshot snapshot,
            String nameOrAlias
    ) {
        String canonical = snapshot.canonicalQueryModelName(nameOrAlias);
        QueryModel model = snapshot.resolveQueryModel(canonical)
                .orElseThrow(() -> new IllegalStateException(
                        "committed catalog does not contain query model " + canonical));
        ModelProvenance modelProvenance = snapshot.queryModelProvenance(canonical).orElse(null);
        return new CatalogResolution<>(
                canonical,
                model,
                snapshot.identity(),
                modelProvenance == null ? Map.of() : modelProvenance.datasourceBindings(),
                modelProvenance != null && modelProvenance.bindingIdentityComplete()
        );
    }

    public Set<String> discoverQueryModelNames(String namespace) {
        String canonicalNamespace = normalizeNamespace(namespace);
        TreeSet<String> names = new TreeSet<>();
        if (systemBundlesContext == null || systemBundlesContext.getBundleList() == null) {
            return Collections.unmodifiableSet(names);
        }
        for (Bundle bundle : systemBundlesContext.getBundleList()) {
            String bundleNamespace = bundle == null || bundle.getDefinition() == null
                    ? ""
                    : normalizeNamespace(bundle.getDefinition().getNamespace());
            if (bundle == null || !canonicalNamespace.equals(bundleNamespace)) {
                continue;
            }
            BundleResource[] resources = bundle.findBundleResources("**/*.qm");
            if (resources == null) {
                continue;
            }
            for (BundleResource resource : resources) {
                String canonicalName = canonicalResourceModelName(resource);
                if (!names.add(canonicalName)) {
                    throw new IllegalStateException(
                            "duplicate query model resource in namespace: " + canonicalName);
                }
            }
        }
        return Collections.unmodifiableSet(names);
    }

    @Override
    public QueryModel loadJdbcQueryModel(BundleResource bundleResource) {
        String namespace = getNamespaceFromBundleResource(bundleResource);
        String normalizedNs = normalizeNamespace(namespace);
        try (NamespaceScope ignored = NamespaceContext.open(normalizedNs)) {
            return resolveWithSingleFlight(
                    "<bundle-resource>", normalizedNs, bundleResource).model();
        }
    }

    private String canonicalResourceModelName(BundleResource bundleResource) {
        String filename = bundleResource == null || bundleResource.getResource() == null
                ? null
                : bundleResource.getResource().getFilename();
        if (filename == null || !filename.endsWith(".qm") || filename.length() <= 3) {
            throw new IllegalStateException("query model resource must have a canonical .qm filename");
        }
        return filename.substring(0, filename.length() - 3);
    }

    /**
     * 从BundleResource中提取namespace
     */
    private String getNamespaceFromBundleResource(BundleResource bundleResource) {
        if (bundleResource == null || bundleResource.getBundle() == null) {
            return "";
        }

        Bundle bundle = bundleResource.getBundle();
        com.foggyframework.core.bundle.BundleDefinition bundleDef = bundle.getDefinition();

        if (bundleDef == null) {
            return "";
        }

        String namespace = bundleDef.getNamespace();
        return namespace != null ? namespace : "";
    }

    /**
     * 执行 QM 脚本，并注入 V2 内置函数
     *
     * @param fsscript QM 脚本
     * @return 执行后的 ExpEvaluator
     */
    private ExpEvaluator evalQmScript(Fsscript fsscript) {
        ExpEvaluator ee = fsscript.newInstance(systemBundlesContext.getApplicationContext());
        // 注入 loadTableModel 内置函数（支持 V2 格式）
        ee.getCurrentFsscriptClosure().setVar("loadTableModel",
                com.foggyframework.dataset.db.model.proxy.LoadTableModelFunction.getInstance());
        fsscript.eval(ee);
        return ee;
    }

    private QueryModelSupport loadJdbcQueryModel(ExpEvaluator ee, Fsscript fsscript, DbQueryModelDef queryModelDef) {
        if (queryModelDef == null) {
            throw RX.throwAUserTip(DatasetMessages.querymodelExportMissing(fsscript.getPath()));
        }
        if (StringUtils.isEmpty(queryModelDef.getModel())) {
            throw RX.throwAUserTip(DatasetMessages.querymodelModelMissing(queryModelDef.getName()));
        }

        /**
         * 构建JdbcQueryModelImpl
         */
        QueryModelSupport qm = null;
        for (QueryModelBuilder queryModelBuilder : queryModelBuilders) {
            qm = queryModelBuilder.build(queryModelDef, fsscript);
            if (qm != null) {
                break;
            }
        }
        if (qm == null) {
            throw RX.throwAUserTip("无法找到对应的QueryModelBuilder");
        }

        queryModelDef.apply(qm);

        /**
         * step10.加载columnGroups中的列
         */
        loadColumnGroups(qm, queryModelDef);
        /**
         * step20.构建查询条件JdbcQueryCond，原则上，所有的select列都需要有查询条件
         */
        //先生成QM中定义的条件
        List<QueryConditionDef> conds = queryModelDef.getConds();
        List<DbQueryCondition> dbQueryConditions = new ArrayList<>();
        if (conds != null) {
            for (QueryConditionDef cond : conds) {
                String field = cond.getField();
                String column = cond.getColumn();
                RX.hasText(column, String.format("查询模型%s中条件%s的column属性不能为空", queryModelDef.getName(), cond));

                DbColumn jdbcColumn = qm.findJdbcColumnForCond(column, true);
                RX.notNull(jdbcColumn, String.format("查询模型%s中通过条件的field:%s未能找到JdbcColumn", queryModelDef.getName(), cond));

                DbQueryConditionImpl jdbcQueryCond = new DbQueryConditionImpl();
                cond.apply(jdbcQueryCond);
                jdbcQueryCond.setQueryModel(qm);
                jdbcQueryCond.setColumn(jdbcColumn);
                if (StringUtils.isEmpty(jdbcQueryCond.getName())) {
                    //如果条件没有定义 name,则默认同它的jdbcColumn
                    jdbcQueryCond.setName(jdbcColumn.getName());
                }

                dbQueryConditions.add(jdbcQueryCond);
                DbDimensionColumn dimensionColumn = jdbcColumn.getDecorate(DbDimensionColumn.class);
                if (dimensionColumn != null) {
                    qm.addQueryDimensionIfNotExist(dimensionColumn.getDimension());
                    jdbcQueryCond.setDimension(dimensionColumn.getDimension());
                }
                DbPropertyColumn dbPropertyColumn = jdbcColumn.getDecorate(DbPropertyColumn.class);
                if (dbPropertyColumn != null) {
                    qm.addQueryPropertyIfNotExist(dbPropertyColumn.getProperty());
                    jdbcQueryCond.setProperty(dbPropertyColumn.getProperty());
                }


            }
        }
        qm.addJdbcQueryConds(dbQueryConditions);
        /**
         * step30.为JdbcQueryColumn补jdbcQueryCond
         */
        for (DbQueryColumn dbQueryColumn : qm.getDbQueryColumns()) {
            String condColumnName = dbQueryColumn.getName();
            DbColumn jdbcColumn = dbQueryColumn.getSelectColumn();
            DbDimensionColumn dimensionColumn = jdbcColumn.getDecorate(DbDimensionColumn.class);
            if (dimensionColumn != null && dimensionColumn.isCaptionColumn()) {
                //这里比较特殊,如果是维度的标题列，则应当用id列来查，但是，如果条件中已经定义了condColumnName这个查询条件，则以查询条件中定义的为准！
                DbQueryCondition dbQueryCondition = qm.findJdbcQueryCondByName(condColumnName);
                if (dbQueryCondition == null) {
                    condColumnName = dimensionColumn.getDimension().getForeignKeyDbColumn().getName();
                }

            }
            if (jdbcColumn.isDimension()) {
                //把该维度加到列表
                qm.addQueryDimensionIfNotExist(jdbcColumn.getDecorate(DbDimensionColumn.class).getDimension());
            } else if (jdbcColumn.isProperty()) {
                //把该维度加到列表
                qm.addQueryPropertyIfNotExist(jdbcColumn.getDecorate(DbPropertyColumn.class).getProperty());
            }

            DbQueryCondition dbQueryCondition = qm.findJdbcQueryCondByName(condColumnName);

            if (dbQueryCondition == null) {
                //该selectColumn没有关联的jdbcQueryCond？定义一个
                dbQueryCondition = autoCreateJdbcQueryCond(qm, dbQueryColumn, qm.findJdbcColumnForCond(condColumnName, true));
                qm.addJdbcQueryCond(dbQueryCondition);
            }
//            jdbcQueryColumn.getDecorate(JdbcQueryColumnImpl.class).setd(jdbcQueryCond);
            dbQueryColumn.getDecorate(DbQueryColumnImpl.class).setDbQueryCondition(dbQueryCondition);
        }
        /**
         * step35.加载orders
         */
        loadOrders(qm, queryModelDef.getOrders());

        /**
         * step40.加载权限数据
         */
        loadAccesses(qm, queryModelDef.getAccesses());

        /**
         * step42.加载成员权限配置
         */
        loadMemberPermissions(qm, queryModelDef.getMemberPermissions());

        /**
         * step50.补一些默认值
         */
        for (DbQueryCondition dbQueryCondition : qm.getDbQueryConditions()) {
            fixJdbcQueryCond(qm, (DbQueryConditionImpl) dbQueryCondition, qm.findJdbcColumnForCond(dbQueryCondition.getName(), false));
        }

        /**
         * 呃，如果存在ID列，默认用它来排
         */
        DbQueryColumn idQueryColumn = qm.getIdJdbcQueryColumn();
        if (idQueryColumn != null) {
            boolean inOrder = false;
            for (DbQueryOrderColumnImpl order : qm.getOrders()) {
                if (order.getSelectColumn() == idQueryColumn.getSelectColumn()) {
                    inOrder = true;
                }
            }
            if (!inOrder) {
                qm.addOrder(idQueryColumn.getSelectColumn(), "desc");
            }
        }

        /**
         * step60.构建物理列映射缓存（QM 字段名 ↔ 物理 table.column）
         */
        qm.setPhysicalColumnMapping(PhysicalColumnMappingBuilder.build(qm));

        return qm;
    }

    private void loadOrders(QueryModelSupport qm, List<OrderDef> orders) {
        if (orders != null) {
            for (int i = 0; i < orders.size(); i++) {
                OrderDef d = orders.get(i);
                ColumnRef ref = toColumnRef(d.getRef());
                if (ref != null) {
                    DbColumn ownerColumn = ColumnRefResolver.resolveColumn(qm, ref);
                    RX.notNull(ownerColumn, "排序字段不存在于 ColumnRef 所属 TableModel: " + ref);
                    qm.addOrder(ownerColumn, d);
                } else {
                    qm.addOrder(qm.findJdbcQueryColumnByName(d.getName(), true).getSelectColumn(), d);
                }
            }
        }
    }

    /**
     * 加载columnGroups中的列,注意，此时不关联查询条件
     *
     * @param qm
     * @param queryModelDef
     */
    private void loadColumnGroups(QueryModelSupport qm, DbQueryModelDef queryModelDef) {
        if (queryModelDef.getColumnGroups() != null && !queryModelDef.getColumnGroups().isEmpty()) {
            List<QueryColumnGroup> columnGroups = new ArrayList<>();
            List<CalculatedFieldDef> predefined = new ArrayList<>();

            // 预扫描所有列组：收集显式引用的列名和维度路径，用于维度展开时跳过重复
            Set<String> explicitColumnNames = new HashSet<>();
            Set<String> explicitDimensionRefs = new HashSet<>();
            for (DbColumnGroupDef scanGroup : queryModelDef.getColumnGroups()) {
                if (scanGroup.getItems() == null) continue;
                for (SelectColumnDef scanItem : scanGroup.getItems()) {
                    if (scanItem == null || StringUtils.isNotEmpty(scanItem.getFormula())) continue;
                    String scanAliasRef = scanItem.getRefAsString();
                    String scanLookupRef = scanItem.getRefForLookup();
                    boolean scanHasRef = StringUtils.isNotEmpty(scanAliasRef);
                    String scanDimRef = scanHasRef ? scanLookupRef : scanItem.getName();
                    String scanColumnName = scanHasRef ? scanAliasRef : scanItem.getName();
                    // A ColumnRef such as product$brand carries a dimension path,
                    // but it is an explicit property column, not an instruction to
                    // expand product.  Resolve an exact owner column before asking
                    // for a dimension so explicit-property detection remains intact.
                    DbColumn scanColumn = resolveColumnForItem(qm, scanItem, scanColumnName);
                    DbDimension scanDimension = scanColumn == null
                            ? resolveDimensionForItem(qm, scanItem, scanDimRef)
                            : null;
                    if (scanDimension != null) {
                        explicitDimensionRefs.add(scanDimRef);
                    } else {
                        explicitColumnNames.add(scanColumnName);
                    }
                }
            }

            for (DbColumnGroupDef columnGroupDef : queryModelDef.getColumnGroups()) {
                if (columnGroupDef.getItems() == null || columnGroupDef.getItems().isEmpty()) {
                    continue;
                }
                QueryColumnGroup group = new QueryColumnGroup();
                group.setCaption(columnGroupDef.getCaption());

                for (SelectColumnDef item : columnGroupDef.getItems()) {
                    if (item == null) {
                        continue;
                    }

                    if (log.isDebugEnabled()) {
                        log.debug("loadColumnGroups [{}] item: name={}, formula={}, ref={}, caption={}",
                                columnGroupDef.getCaption(),
                                item.getName(), item.getFormula(),
                                item.getRef() != null ? item.getRef().getClass().getSimpleName() : "null",
                                item.getCaption());
                    }

                    // formula 项 → 转为 CalculatedFieldDef，不走常规列加载
                    if (StringUtils.isNotEmpty(item.getFormula())) {
                        CalculatedFieldDef calc = new CalculatedFieldDef();
                        calc.setName(item.getName());
                        calc.setCaption(item.getCaption());
                        calc.setExpression(item.getFormula());
                        calc.setType(item.getType());
                        calc.setDescription(item.getDescription());
                        calc.setEmptyDefault(item.getEmptyDefault());
                        calc.setPartitionBy(item.getPartitionBy());
                        calc.setWindowOrderBy(convertWindowOrderBy(item.getWindowOrderBy()));
                        calc.setWindowFrame(item.getWindowFrame());
                        predefined.add(calc);
                        // formula 项不加入常规列查找（通过 calculatedFields 机制处理）
                        continue;
                    }

                    // V2 格式：ref 可能是 ColumnRef 对象
                    // aliasRef: 使用 _ 分隔，用于列名/别名和列查找
                    String aliasRef = item.getRefAsString();
                    // lookupRef: 使用 . 分隔，用于在 TableModel 中查找维度
                    String lookupRef = item.getRefForLookup();
                    boolean hasRef = StringUtils.isNotEmpty(aliasRef);

                    // 如果有 ref 且没有显式指定 name/alias，使用 aliasRef 作为默认值
                    if (hasRef) {
                        if (StringUtils.isEmpty(item.getName())) {
                            item.setName(aliasRef);
                        }
                        if (StringUtils.isEmpty(item.getAlias())) {
                            item.setAlias(aliasRef);
                        }
                    }

                    // 使用 lookupRef 进行维度查找（dot格式支持嵌套路径）
                    String dimRef = hasRef ? lookupRef : item.getName();
                    // 使用 aliasRef 作为列名和列查找（使用 _ 分隔，因为列索引用alias格式）
                    String columnName = hasRef ? aliasRef : item.getName();

                    DbColumn exactColumn = resolveColumnForItem(qm, item, columnName);
                    DbDimension dimension = exactColumn == null
                            ? resolveDimensionForItem(qm, item, dimRef)
                            : null;
                    if (dimension != null) {
                        TableModel ownerModel = resolveOwnerModelForItem(qm, item);
                        //维度，自动展开 $id + $caption + 所有属性 + 嵌套子维度（递归）
                        expandDimension(qm, group, ownerModel, dimension, columnName, item, hasRef,
                                explicitColumnNames, explicitDimensionRefs);
                    } else {
                        addColumn(qm, group, columnName, item, hasRef, exactColumn);
                    }
                }
                columnGroups.add(group);
            }
            qm.setColumnGroups(columnGroups);
            qm.setPredefinedCalculatedFields(predefined);
        }
    }

    /**
     * 将 QM 中 windowOrderBy 的 {@code List<Map>} 转换为 {@code List<WindowOrderDef>}
     * <p>
     * {@code SelectColumnDef.windowOrderBy} 声明为 {@code List<Map<String, Object>>}，
     * 这样 FsscriptConversionService 转换时会保留 Map 结构（避免被 MapToObjectConverter
     * 误转为空 Object 实例）。
     * </p>
     */
    private List<WindowOrderDef> convertWindowOrderBy(List<Map<String, Object>> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return null;
        }
        List<WindowOrderDef> result = new ArrayList<>(rawList.size());
        for (Map<String, Object> map : rawList) {
            if (map == null) {
                continue;
            }
            String field = map.get("field") != null ? map.get("field").toString() : null;
            String dir = map.get("dir") != null ? map.get("dir").toString() : null;
            if (field != null) {
                result.add(new WindowOrderDef(field, dir));
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 展开维度为 $id + $caption + 所有属性 + 嵌套子维度（递归）。
     * <p>
     * 展开策略：
     * - $id + $caption 始终添加
     * - 属性：仅当该维度没有任何显式属性引用时才自动展开全部属性；
     *   若 QM 已显式引用了部分属性（如 fo.customer$gender），说明作者有意选择，不再自动补全
     * - 嵌套子维度：若子维度已被 QM 显式引用（如 fo.product.category），则跳过（由其自身的 ref 展开）
     */
    private void expandDimension(QueryModelSupport qm, QueryColumnGroup group, TableModel ownerModel,
                                  DbDimension dimension, String columnName,
                                  SelectColumnDef item, boolean hasRef,
                                  Set<String> explicitColumnNames,
                                  Set<String> explicitDimensionRefs) {
        // 1. $id + $caption（必加）
        String idColumnName = columnName + "$id";
        String captionColumnName = columnName + "$caption";
        addColumn(qm, group, idColumnName, createExpandedDimensionItem(item, idColumnName), hasRef,
                findDimensionColumn(ownerModel, dimension, columnName + "$id"));
        addColumn(qm, group, captionColumnName, createExpandedDimensionItem(item, captionColumnName), hasRef,
                findDimensionColumn(ownerModel, dimension, columnName + "$caption"));

        // 2. 展开属性：仅当该维度没有任何显式属性引用时
        if (dimension instanceof DbDimensionSupport) {
            String basePath = dimension.getFullPathForAlias();
            // 检查是否有任何该维度的显式属性引用
            String propPrefix = basePath + "$";
            boolean hasExplicitProps = explicitColumnNames.stream()
                    .anyMatch(name -> name.startsWith(propPrefix));

            if (!hasExplicitProps) {
                // 无显式属性引用 → 自动展开全部属性
                for (DbProperty prop : ((DbDimensionSupport) dimension).getJdbcProperties()) {
                    String propColumnName = basePath + "$" + prop.getName();
                    addColumn(qm, group, propColumnName, createAutoExpandedPropertyItem(item, propColumnName), hasRef,
                            findDimensionColumn(ownerModel, dimension, propColumnName));
                }
            }
        }

        // 3. 递归展开嵌套子维度（跳过已被 QM 显式引用的子维度）
        if (dimension.hasChildDimensions()) {
            for (DbDimension child : dimension.getChildDimensions()) {
                String childDimPath = child.getFullPath();
                if (explicitDimensionRefs.contains(childDimPath)) {
                    // 该子维度已被 QM 显式引用，由其自身的 ref 展开，此处跳过
                    continue;
                }
                String childColumnName = child.getFullPathForAlias();
                expandDimension(qm, group, ownerModel, child, childColumnName, item, hasRef,
                        explicitColumnNames, explicitDimensionRefs);
            }
        }
    }

    private DbColumn resolveColumnForItem(QueryModelSupport qm, SelectColumnDef item, String columnName) {
        ColumnRef ref = item.getRefAsColumnRef();
        if (ref != null) {
            return ColumnRefResolver.resolveColumn(qm, ref);
        }
        return qm.findJdbcColumn(columnName);
    }

    private DbDimension resolveDimensionForItem(QueryModelSupport qm, SelectColumnDef item, String dimensionName) {
        ColumnRef ref = item.getRefAsColumnRef();
        if (ref != null) {
            return ColumnRefResolver.resolveDimension(qm, ref);
        }
        return qm.findDimension(dimensionName);
    }

    private TableModel resolveOwnerModelForItem(QueryModelSupport qm, SelectColumnDef item) {
        ColumnRef ref = item.getRefAsColumnRef();
        if (ref == null) {
            return null;
        }
        List<TableModel> owners = ColumnRefResolver.resolveOwnerModels(qm, ref);
        return owners.isEmpty() ? null : owners.get(0);
    }

    private DbColumn findDimensionColumn(TableModel ownerModel, DbDimension dimension, String columnName) {
        if (ownerModel != null) {
            DbColumn ownerColumn = ownerModel.findJdbcColumnByName(
                    stripOwnerQualifier(ownerModel, columnName));
            if (ownerColumn != null) {
                return ownerColumn;
            }
        }
        if (dimension == null || dimension.getAllDbColumns() == null) {
            return null;
        }
        for (DbColumn column : dimension.getAllDbColumns()) {
            if (column != null && StringUtils.equals(columnName, column.getName())) {
                return column;
            }
        }
        return null;
    }

    private String stripOwnerQualifier(TableModel ownerModel, String columnName) {
        if (ownerModel == null || StringUtils.isEmpty(columnName)) {
            return columnName;
        }
        String prefix = ownerModel.getAlias() + ".";
        return columnName.startsWith(prefix) ? columnName.substring(prefix.length()) : columnName;
    }

    private ColumnRef toColumnRef(Object ref) {
        if (ref instanceof ColumnRef columnRef) {
            return columnRef;
        }
        if (ref instanceof DimensionProxy dimensionProxy) {
            return dimensionProxy.toColumnRef();
        }
        return null;
    }

    private void addColumn(QueryModelSupport qm, QueryColumnGroup group, String columnName, SelectColumnDef item,
                           boolean hasRef, DbColumn resolvedColumn) {
        // columnName 使用 alias 格式（_ 分隔），因为列在 TableModel 中以 alias 格式索引
        DbColumn jdbcColumn = resolvedColumn != null
                ? resolvedColumn
                : qm.findJdbcColumnForCond(columnName, true);

        /**
         * 创建 DbQueryColumn 并设置字段名相关属性：
         *
         * @param jdbcColumn 从 TableModel 中找到的列
         * @param columnName 列名（name），用于在 QM 中标识列（通过 findJdbcQueryColumnByName 查找）
         * @param item.getCaption() 列标题
         * @param item.getAlias() 别名（alias），用户在 QM 中定义的列别名，用于避免多模型 JOIN 时重名
         *
         * 说明：
         * - name: 列的唯一标识，用于在 QM 中查找列
         * - alias: 用户定义的别名，用于重命名字段（如 { ref: dc.customerType, alias: 'custType' }）
         */
        String queryColumnName = StringUtils.isNotEmpty(item.getName()) ? item.getName() : columnName;
        DbQueryColumn dbQueryColumn = new DbQueryColumnImpl(jdbcColumn, queryColumnName, item.getCaption(), item.getAlias());
        dbQueryColumn.setHasRef(hasRef);

        qm.addJdbcQueryColumn(dbQueryColumn);
        group.addJdbcColumn(dbQueryColumn);
    }

    private SelectColumnDef createAutoExpandedPropertyItem(SelectColumnDef item, String propColumnName) {
        SelectColumnDef autoExpandedItem = new SelectColumnDef();
        BeanUtils.copyProperties(item, autoExpandedItem);
        autoExpandedItem.setName(propColumnName);
        autoExpandedItem.setAlias(propColumnName);
        // 自动展开属性应回落到各自列的 caption，不能复用维度入口列的 caption。
        autoExpandedItem.setCaption(null);
        return autoExpandedItem;
    }

    private SelectColumnDef createExpandedDimensionItem(SelectColumnDef item, String columnName) {
        SelectColumnDef expandedItem = new SelectColumnDef();
        BeanUtils.copyProperties(item, expandedItem);
        expandedItem.setName(columnName);
        expandedItem.setAlias(columnName);
        return expandedItem;
    }

    private void fixJdbcQueryCond(QueryModelSupport qm, DbQueryConditionImpl jdbcQueryCond, DbColumn selectColumn) {
        if (selectColumn == null) {
            return;
        }
        if (StringUtils.isEmpty(jdbcQueryCond.getCaption())) {
            jdbcQueryCond.setCaption(selectColumn.getCaption());
        }
        DbDimensionColumn dbDimensionColumn = selectColumn.getDecorate(DbDimensionColumn.class);
        DbMeasureColumn jdbcMeasureColumn = selectColumn.getDecorate(DbMeasureColumn.class);
        DbPropertyColumn dbPropertyColumn = selectColumn.getDecorate(DbPropertyColumn.class);
        String autoQueryType = "=";
        DbQueryCondType autoType = null;

        if (dbDimensionColumn != null && dbPropertyColumn == null) {
            //仅当jdbcQueryCond中的jdbcColumn为foreignKey列时，才使用下拉查询

            DbDimension dimension = dbDimensionColumn.getDimension();
            QueryObject dimQueryObject = dimension.getQueryObject();
            jdbcQueryCond.setDimension(dimension);

            if (dimQueryObject != null && !dbDimensionColumn.isCaptionColumn()) {
                //有关联的维表,且不是caption列
                jdbcQueryCond.setType(DbQueryCondType.DIM);

            } else {
                DbDimensionType dimType = dimension.getType();
                Tuple2<String, DbQueryCondType> r = autoFix(jdbcQueryCond, dimType);
                autoQueryType = r.getT1();
                autoType = r.getT2();
            }


        } else if (jdbcMeasureColumn != null) {
            if (jdbcQueryCond.getType() == null) {
                // 度量类型不直接映射到查询条件类型，保持为 null 让后续逻辑处理
            }
            autoQueryType = "[]";
        } else if (dbPropertyColumn != null) {
            jdbcQueryCond.setProperty(dbPropertyColumn.getProperty());
            DbDimensionType dimType = DbDimensionType.fromColumnType(dbPropertyColumn.getProperty().getType());
            Tuple2<String, DbQueryCondType> r = autoFix(jdbcQueryCond, dimType);
            autoQueryType = r.getT1();
            autoType = r.getT2();
        }

        if (jdbcQueryCond.getType() == null) {
            jdbcQueryCond.setType(autoType);
        }
        if (StringUtils.isEmpty(jdbcQueryCond.getQueryType())) {
            jdbcQueryCond.setQueryType(autoQueryType);
        }
    }

    private Tuple2<String, DbQueryCondType> autoFix(DbQueryConditionImpl jdbcQueryCond, DbDimensionType type) {
        String autoQueryType = "=";
        DbQueryCondType autoType = null;
        if (type == DbDimensionType.DATETIME) {
            //日期维
            autoQueryType = "[)";
            autoType = DbQueryCondType.DATE_RANGE;
        } else if (type == DbDimensionType.DICT) {
            //字典表维
            autoType = DbQueryCondType.DICT;
        } else if (type == DbDimensionType.BOOL) {
            //boolean
            autoType = DbQueryCondType.BOOL;
        } else if (type == DbDimensionType.DOUBLE) {
            //boolean
            autoType = DbQueryCondType.DOUBLE;
        } else if (type == DbDimensionType.INTEGER) {
            //boolean
            autoType = DbQueryCondType.INTEGER;
        } else if (type == DbDimensionType.DAY) {
            //字典表维
            autoQueryType = "[]";
            autoType = DbQueryCondType.DAY_RANGE;
        } else {
            jdbcQueryCond.setType(DbQueryCondType.COMMON);
            autoType = DbQueryCondType.COMMON;
        }
        return new Tuple2<>(autoQueryType, autoType);
    }

    private DbQueryConditionImpl autoCreateJdbcQueryCond(QueryModelSupport qm, DbQueryColumn dbQueryColumn, DbColumn selectColumn) {


        DbQueryConditionImpl jdbcQueryCond = new DbQueryConditionImpl();
        jdbcQueryCond.setQueryModel(qm);
        jdbcQueryCond.setColumn(selectColumn);
        if (dbQueryColumn.isHasRef()) {
            jdbcQueryCond.setField(dbQueryColumn.getField());
            jdbcQueryCond.setName(dbQueryColumn.getName());
        } else {
            jdbcQueryCond.setField(selectColumn.getField());
            jdbcQueryCond.setName(selectColumn.getName());
        }


        fixJdbcQueryCond(qm, jdbcQueryCond, selectColumn);

        return jdbcQueryCond;
    }

    private void loadAccesses(QueryModelSupport qm, List<DbAccessDef> accessDefs) {
        if (accessDefs == null) {
            return;
        }
        for (DbAccessDef accessDef : accessDefs) {
            // 简化后：直接收集 queryBuilder，不再强制绑定到 dimension/property
            if (accessDef.getQueryBuilder() != null) {
                qm.getAccessBuilders().add(accessDef.getQueryBuilder());
            }
        }
    }

    private void loadMemberPermissions(QueryModelSupport qm,
                                        List<QmMemberPermissionDef> memberPermissions) {
        if (memberPermissions == null || memberPermissions.isEmpty()) {
            return;
        }
        qm.setMemberPermissions(memberPermissions);
    }

}
