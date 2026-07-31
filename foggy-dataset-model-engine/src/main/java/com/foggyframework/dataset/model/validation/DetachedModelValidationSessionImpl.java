package com.foggyframework.dataset.model.validation;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.conversion.FsscriptConversionService;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.model.def.DbModelDef;
import com.foggyframework.dataset.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.proxy.LoadTableModelFunction;
import com.foggyframework.dataset.model.spi.DetachedQueryModelBuilderFactory;
import com.foggyframework.dataset.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.TableModelLoader;
import com.foggyframework.dataset.model.spi.TableModelLoaderManager;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.RootFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Request-local Runtime model validator.
 *
 * <p>The source bundle is deliberately not registered in the live
 * {@link SystemBundlesContext}. Script caches and model catalogs are also
 * request-local, so successful and failed validation cannot publish aliases,
 * names, models, or generations into the running application.</p>
 */
final class DetachedModelValidationSessionImpl
        implements DetachedModelValidationSession {

    private static final String VALIDATION_CONTEXT_BEAN =
            "runtimeDetachedValidationSystemBundlesContext";

    private final SystemBundlesContext liveBundlesContext;
    private final TableModelLoaderManager liveTableModelLoaderManager;
    private final QueryModelLoader liveQueryModelLoader;
    private final DetachedSystemBundlesContext detachedBundlesContext;
    private final ExternalFileBundle sourceBundle;

    private RootFsscriptLoader rootFsscriptLoader;
    private FileFsscriptLoader fileFsscriptLoader;
    private TableModelLoaderManager detachedTableModelLoaderManager;
    private QueryModelLoader detachedQueryModelLoader;
    private CatalogSnapshotStore detachedCatalog;
    private boolean closed;

    DetachedModelValidationSessionImpl(
            SystemBundlesContext liveBundlesContext,
            TableModelLoaderManager liveTableModelLoaderManager,
            QueryModelLoader liveQueryModelLoader,
            String bundleName,
            String namespace,
            String path
    ) {
        this.liveBundlesContext = liveBundlesContext;
        this.liveTableModelLoaderManager = liveTableModelLoaderManager;
        this.liveQueryModelLoader = liveQueryModelLoader;
        this.detachedBundlesContext = new DetachedSystemBundlesContext(liveBundlesContext);

        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                bundleName,
                namespace,
                path,
                false
        );
        this.sourceBundle = new ExternalFileBundle(detachedBundlesContext);
        this.sourceBundle.setName(definition.getName());
        this.sourceBundle.setBundleDefinition(definition);
        this.sourceBundle.setBasePath(definition.getPath());
        this.sourceBundle.setRootPath(definition.getPath());
        this.detachedBundlesContext.attach(sourceBundle);
    }

    @Override
    public Bundle sourceBundle() {
        requireOpen();
        return sourceBundle;
    }

    @Override
    public void validateTableModel(BundleResource resource, String namespace) {
        ensureLoaders();
        if (detachedTableModelLoaderManager != null) {
            detachedTableModelLoaderManager.load(modelName(resource, ".tm"), namespace);
            return;
        }
        validateTableDefinition(resource);
    }

    @Override
    public void validateQueryModel(BundleResource resource) {
        ensureLoaders();
        if (detachedQueryModelLoader != null) {
            detachedQueryModelLoader.loadJdbcQueryModel(resource);
            return;
        }
        validateQueryDefinition(resource);
    }

    @Override
    public CatalogResolution<QueryModel> resolveQueryModel(
            String queryModelName,
            String namespace
    ) {
        ensureLoaders();
        if (detachedQueryModelLoader == null) {
            throw new IllegalStateException(
                    "Detached query resolution requires production TM/QM loaders");
        }
        return detachedQueryModelLoader.resolveJdbcQueryModel(
                queryModelName, namespace);
    }

    @Override
    public SystemBundlesContext executionBundlesContext() {
        ensureLoaders();
        return detachedBundlesContext;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException cleanupFailure = null;
        try {
            if (detachedCatalog != null) {
                cleanupFailure = cleanup(
                        cleanupFailure, detachedCatalog::clearAll);
            }
            if (rootFsscriptLoader != null) {
                cleanupFailure = cleanup(
                        cleanupFailure, rootFsscriptLoader::clear);
            }
            cleanupFailure = cleanup(cleanupFailure, sourceBundle::clearCache);
        } finally {
            detachedBundlesContext.setApplicationContext(null);
            rootFsscriptLoader = null;
            fileFsscriptLoader = null;
            detachedTableModelLoaderManager = null;
            detachedQueryModelLoader = null;
            detachedCatalog = null;
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private void ensureLoaders() {
        requireOpen();
        if (fileFsscriptLoader != null) {
            return;
        }
        ApplicationContext liveApplicationContext = liveBundlesContext.getApplicationContext();
        if (liveApplicationContext == null) {
            throw new IllegalStateException(
                    "Detached model validation requires the runtime ApplicationContext");
        }

        ApplicationContext validationApplicationContext = validationApplicationContext(
                liveApplicationContext,
                detachedBundlesContext
        );
        detachedBundlesContext.setApplicationContext(validationApplicationContext);
        rootFsscriptLoader = new RootFsscriptLoader(validationApplicationContext);
        fileFsscriptLoader = new FileFsscriptLoader(
                validationApplicationContext,
                rootFsscriptLoader,
                null
        );

        if (liveTableModelLoaderManager instanceof TableModelLoaderManagerImpl liveTableManager
                && liveQueryModelLoader instanceof QueryModelLoaderImpl liveQueryLoader) {
            detachedCatalog = new CatalogSnapshotStore();
            detachedTableModelLoaderManager = detachedTableModelLoaderManager(
                    liveTableManager,
                    detachedCatalog
            );
            detachedQueryModelLoader = detachedQueryModelLoader(
                    liveQueryLoader,
                    (TableModelLoaderManagerImpl) detachedTableModelLoaderManager,
                    detachedCatalog
            );
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "Detached model validation session is closed");
        }
    }

    private static RuntimeException cleanup(
            RuntimeException previous,
            Runnable action
    ) {
        try {
            action.run();
            return previous;
        } catch (RuntimeException failure) {
            if (previous == null) {
                return failure;
            }
            previous.addSuppressed(failure);
            return previous;
        }
    }

    private TableModelLoaderManagerImpl detachedTableModelLoaderManager(
            TableModelLoaderManagerImpl live,
            CatalogSnapshotStore detachedCatalog
    ) {
        List<TableModelLoader> loaders = new ArrayList<>(
                live.getTypeName2Loader() == null
                        ? List.of()
                        : live.getTypeName2Loader().values()
        );
        TableModelLoaderManagerImpl detached = new TableModelLoaderManagerImpl(
                detachedBundlesContext,
                fileFsscriptLoader,
                live.getProcessors(),
                loaders,
                live.getNamedDataSourceResolver(),
                live.getDatasetProperties(),
                detachedCatalog
        );
        detached.setDataSource(live.getDataSource());
        return detached;
    }

    private QueryModelLoaderImpl detachedQueryModelLoader(
            QueryModelLoaderImpl live,
            TableModelLoaderManagerImpl detachedTableManager,
            CatalogSnapshotStore detachedCatalog
    ) {
        List<QueryModelBuilder> builders = detachedQueryModelBuilders(
                live.getQueryModelBuilders(),
                detachedTableManager,
                detachedBundlesContext,
                fileFsscriptLoader
        );
        QueryModelLoaderImpl detached = new QueryModelLoaderImpl(
                detachedTableManager,
                detachedBundlesContext,
                fileFsscriptLoader,
                builders,
                detachedCatalog
        );
        detached.setDefaultDataSource(live.getDefaultDataSource());
        detached.setSyntheticMemberQueryModelFactory(
                live.getSyntheticMemberQueryModelFactory());
        return detached;
    }

    static List<QueryModelBuilder> detachedQueryModelBuilders(
            List<QueryModelBuilder> liveBuilders,
            TableModelLoaderManager detachedTableManager,
            SystemBundlesContext detachedBundlesContext,
            FileFsscriptLoader detachedFileFsscriptLoader
    ) {
        if (liveBuilders == null || liveBuilders.isEmpty()) {
            return List.of();
        }
        List<QueryModelBuilder> detachedBuilders = new ArrayList<>();
        for (QueryModelBuilder liveBuilder : liveBuilders) {
            if (!(liveBuilder instanceof DetachedQueryModelBuilderFactory factory)) {
                continue;
            }
            QueryModelBuilder detached = factory.createDetachedQueryModelBuilder(
                    detachedTableManager,
                    detachedBundlesContext,
                    detachedFileFsscriptLoader
            );
            if (detached == null) {
                throw new IllegalStateException(
                        "Detached QueryModelBuilder factory returned null: "
                                + liveBuilder.getClass().getName());
            }
            detachedBuilders.add(detached);
        }
        return List.copyOf(detachedBuilders);
    }

    private void validateTableDefinition(BundleResource resource) {
        Fsscript fsscript = fileFsscriptLoader.findLoadFsscript(resource);
        ExpEvaluator evaluator = fsscript.eval(detachedBundlesContext.getApplicationContext());
        Object exported = evaluator.getExportObject("model");
        if (exported == null) {
            throw new IllegalStateException("TM does not export model: " + relativeName(resource));
        }
        DbModelDef definition = FsscriptConversionService.getSharedInstance()
                .convert(exported, DbModelDef.class);
        String expectedName = modelName(resource, ".tm");
        if (definition == null || definition.getName() == null
                || !expectedName.equals(definition.getName().trim())) {
            throw new IllegalStateException(
                    "TM resource name '" + expectedName
                            + "' does not match exported canonical name '"
                            + (definition == null ? null : definition.getName()) + "'");
        }
    }

    private void validateQueryDefinition(BundleResource resource) {
        Fsscript fsscript = fileFsscriptLoader.findLoadFsscript(resource);
        ExpEvaluator evaluator = fsscript.newInstance(
                detachedBundlesContext.getApplicationContext());
        evaluator.getCurrentFsscriptClosure().setVar(
                "loadTableModel",
                LoadTableModelFunction.getInstance()
        );
        fsscript.eval(evaluator);
        Object exported = evaluator.getExportObject("queryModel");
        if (exported == null) {
            throw new IllegalStateException(
                    "QM does not export queryModel: " + relativeName(resource));
        }
        DbQueryModelDef definition = FsscriptConversionService.getSharedInstance()
                .convert(exported, DbQueryModelDef.class);
        String expectedName = modelName(resource, ".qm");
        if (definition == null || definition.getName() == null
                || !expectedName.equals(definition.getName().trim())) {
            throw new IllegalStateException(
                    "QM resource name '" + expectedName
                            + "' does not match exported canonical name '"
                            + (definition == null ? null : definition.getName()) + "'");
        }
        if (definition.getModel() == null) {
            throw new IllegalStateException(
                    "QM does not declare a root table model: " + expectedName);
        }
    }

    private static String modelName(BundleResource resource, String suffix) {
        String name = relativeName(resource);
        if (!name.endsWith(suffix) || name.length() <= suffix.length()) {
            throw new IllegalArgumentException(
                    "Model resource must end with " + suffix + ": " + name);
        }
        return name.substring(0, name.length() - suffix.length());
    }

    private static String relativeName(BundleResource resource) {
        String filename = resource == null || resource.getResource() == null
                ? null
                : resource.getResource().getFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Model resource filename is required");
        }
        return filename;
    }

    private static ApplicationContext validationApplicationContext(
            ApplicationContext live,
            DetachedSystemBundlesContext detached
    ) {
        return (ApplicationContext) Proxy.newProxyInstance(
                ApplicationContext.class.getClassLoader(),
                new Class<?>[]{ApplicationContext.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getBean".equals(methodName) && args != null && args.length > 0) {
                        if (args[0] instanceof Class<?> type
                                && type.isInstance(detached)) {
                            return detached;
                        }
                        if (args[0] instanceof String name
                                && VALIDATION_CONTEXT_BEAN.equals(name)) {
                            return detached;
                        }
                    }
                    if ("getBeanProvider".equals(methodName)
                            && args != null && args.length > 0
                            && args[0] instanceof Class<?> type
                            && type.isInstance(detached)) {
                        return new ObjectProvider<SystemBundlesContext>() {
                            @Override
                            public SystemBundlesContext getObject() {
                                return detached;
                            }
                        };
                    }
                    if ("getBeansOfType".equals(methodName)
                            && args != null && args.length > 0
                            && args[0] instanceof Class<?> type
                            && type.isInstance(detached)) {
                        return Map.of(VALIDATION_CONTEXT_BEAN, detached);
                    }
                    if ("getBeanNamesForType".equals(methodName)
                            && args != null && args.length > 0
                            && args[0] instanceof Class<?> type
                            && type.isInstance(detached)) {
                        return new String[]{VALIDATION_CONTEXT_BEAN};
                    }
                    if ("containsBean".equals(methodName)
                            && args != null && args.length == 1
                            && VALIDATION_CONTEXT_BEAN.equals(args[0])) {
                        return true;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (methodName) {
                            case "toString" -> "RuntimeDetachedValidationApplicationContext";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> method.invoke(live, args);
                        };
                    }
                    try {
                        return method.invoke(live, args);
                    } catch (InvocationTargetException invocationFailure) {
                        throw invocationFailure.getCause();
                    }
                }
        );
    }

    private static final class DetachedSystemBundlesContext
            implements SystemBundlesContext {

        private final SystemBundlesContext live;
        private ApplicationContext applicationContext;
        private ExternalFileBundle sourceBundle;

        private DetachedSystemBundlesContext(SystemBundlesContext live) {
            this.live = live;
        }

        private void attach(ExternalFileBundle bundle) {
            this.sourceBundle = bundle;
        }

        private void setApplicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        @Override
        public ApplicationContext getApplicationContext() {
            return applicationContext;
        }

        @Override
        public void regBundle(Bundle bundle) {
            throw readOnly();
        }

        @Override
        public List<Bundle> getBundleList() {
            List<Bundle> bundles = new ArrayList<>();
            bundles.add(sourceBundle);
            for (Bundle bundle : live.getBundleList()) {
                if (bundle != null && !sourceBundle.getName().equals(bundle.getName())) {
                    bundles.add(bundle);
                }
            }
            return List.copyOf(bundles);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public Bundle getBundleByName(String name, boolean throwError) {
            if (sourceBundle.getName().equals(name)) {
                return sourceBundle;
            }
            return live.getBundleByName(name, throwError);
        }

        @Override
        public Bundle getBundleByName(String name) {
            if (sourceBundle.getName().equals(name)) {
                return sourceBundle;
            }
            return live.getBundleByName(name);
        }

        @Override
        public BundleDefinition getBundleDefinitionByName(String name) {
            if (sourceBundle.getName().equals(name)) {
                return sourceBundle.getDefinition();
            }
            return live.getBundleDefinitionByName(name);
        }

        @Override
        public Bundle getBundleByPackageName(String packageName) {
            if (sourceBundle.getPackageName().equals(packageName)) {
                return sourceBundle;
            }
            return live.getBundleByPackageName(packageName);
        }

        @Override
        public Bundle getBundleByClassName(String className, boolean errorIfNotFound) {
            return live.getBundleByClassName(className, errorIfNotFound);
        }

        @Override
        public Bundle getBundleByPackageName(String packageName, boolean errorIfNotFound) {
            if (sourceBundle.getPackageName().equals(packageName)) {
                return sourceBundle;
            }
            return live.getBundleByPackageName(packageName, errorIfNotFound);
        }

        @Override
        public BundleDefinition getBundleDefinitionByPackageName(String packageName) {
            if (sourceBundle.getPackageName().equals(packageName)) {
                return sourceBundle.getDefinition();
            }
            return live.getBundleDefinitionByPackageName(packageName);
        }

        @Override
        public Bundle getBundleByResource(Resource resource) {
            if (belongsToSourceBundle(resource)) {
                return sourceBundle;
            }
            return live.getBundleByResource(resource);
        }

        @Override
        public BundleResource findResourceByName(String name, boolean errorIfNotFound) {
            return findResourceByName(name, sourceBundle.getDefinition().getNamespace(),
                    errorIfNotFound);
        }

        @Override
        public BundleResource findResourceByName(
                String name,
                String namespace,
                boolean errorIfNotFound
        ) {
            String sourceNamespace = canonicalNamespace(
                    sourceBundle.getDefinition().getNamespace());
            if (sourceNamespace.equals(canonicalNamespace(namespace))) {
                BundleResource resource = sourceBundle.findBundleResource(name, false);
                if (resource != null) {
                    return resource;
                }
            }
            return live.findResourceByName(name, namespace, errorIfNotFound);
        }

        @Override
        public boolean containBundle(String bundle) {
            return sourceBundle.getName().equals(bundle) || live.containBundle(bundle);
        }

        @Override
        public boolean addExternalBundle(
                String name,
                String namespace,
                String path,
                boolean watch
        ) {
            throw readOnly();
        }

        @Override
        public boolean replaceExternalBundle(
                String name,
                String namespace,
                String path,
                boolean watch
        ) {
            throw readOnly();
        }

        @Override
        public boolean removeBundle(String bundleName) {
            throw readOnly();
        }

        @Override
        public List<BundleDefinition> listExternalBundles() {
            return List.of(sourceBundle.getDefinition());
        }

        private boolean belongsToSourceBundle(Resource resource) {
            if (resource == null) {
                return false;
            }
            try {
                if (!resource.isFile()) {
                    return false;
                }
                Path root = Paths.get(sourceBundle.getRootPath())
                        .toAbsolutePath().normalize();
                Path candidate = resource.getFile().toPath()
                        .toAbsolutePath().normalize();
                return candidate.startsWith(root);
            } catch (IOException | RuntimeException ignored) {
                return false;
            }
        }

        private static String canonicalNamespace(String namespace) {
            return namespace == null || namespace.isBlank() ? "" : namespace.trim();
        }

        private static UnsupportedOperationException readOnly() {
            return new UnsupportedOperationException(
                    "Detached Runtime validation bundle context is read-only");
        }
    }
}
