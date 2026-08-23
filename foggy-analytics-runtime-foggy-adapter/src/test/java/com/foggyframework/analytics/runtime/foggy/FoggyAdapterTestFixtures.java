package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.definition.api.AnalyticsSchemaVersion;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.TableModel;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

final class FoggyAdapterTestFixtures {

    static final String NAMESPACE = "default";
    static final String ENGINE_NAMESPACE = "";
    static final String MODEL = "SalesOrder";
    static final AnalyticsModelRevision MODEL_REVISION =
            AnalyticsModelRevision.fromSha256Hex("a".repeat(64));
    static final CatalogIdentity CATALOG_IDENTITY = new CatalogIdentity(
            ENGINE_NAMESPACE,
            new CatalogGeneration("catalog:test:1"),
            new SourceRevision("source:test:1"));

    private FoggyAdapterTestFixtures() {
    }

    static AnalyticsModelDependency queryDependency() {
        return dependency("qm", MODEL, MODEL_REVISION);
    }

    static AnalyticsModelDependency dependency(
            String modelKind,
            String modelName,
            AnalyticsModelRevision revision) {
        return new AnalyticsModelDependency(
                new AnalyticsNamespaceRef(NAMESPACE),
                modelKind,
                modelName,
                revision);
    }

    static CatalogResolution<QueryModel> resolution() {
        return resolution(CATALOG_IDENTITY, MODEL);
    }

    static CatalogResolution<QueryModel> resolution(
            CatalogIdentity identity,
            String modelName) {
        return new CatalogResolution<>(
                modelName,
                queryModel(modelName),
                identity,
                Map.of(),
                true);
    }

    static NamespaceCatalogView trackedView() {
        CatalogResolution<QueryModel> resolution = resolution();
        return new NamespaceCatalogView(
                CATALOG_IDENTITY,
                List.of(MODEL),
                Map.of(MODEL, "SO"),
                Map.of(MODEL, resolution.model()),
                Map.of(MODEL, resolution));
    }

    static AnalyticsBundleManifest manifest(List<AnalyticsModelDependency> dependencies) {
        return new AnalyticsBundleManifest(
                AnalyticsBundleManifest.ANALYTICS_KIND,
                AnalyticsSchemaVersion.V1,
                new AnalyticsBundleRef("sales"),
                AnalyticsBundleRevision.fromSha256Hex("b".repeat(64)),
                new AnalyticsNamespaceRef(NAMESPACE),
                dependencies);
    }

    static QueryModel queryModel(String modelName) {
        return queryModel(modelName, null);
    }

    static QueryModel queryModel(String modelName, String shortAlias) {
        return modelProxy(QueryModel.class, modelName, shortAlias);
    }

    static TableModel tableModel(String modelName) {
        return modelProxy(TableModel.class, modelName, null);
    }

    private static <T> T modelProxy(
            Class<T> modelType,
            String modelName,
            String shortAlias) {
        return modelType.cast(Proxy.newProxyInstance(
                modelType.getClassLoader(),
                new Class<?>[]{modelType},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> modelName;
                    case "getShortAlias" -> shortAlias;
                    case "toString" -> modelType.getSimpleName() + "[" + modelName + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                }));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
