package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PivotOuterCacheOperationalSpiTest {

    @Test
    @DisplayName("runtime bundle identity provider changes fingerprint when QM/TM content changes")
    void testRuntimeBundleIdentityProviderChangesWithModelResources() {
        QueryModel queryModel = queryModel("SalesQM", "SalesTM");
        RuntimeBundlePivotOuterCacheModelIdentityProvider firstProvider =
                new RuntimeBundlePivotOuterCacheModelIdentityProvider(bundleContext(Map.of(
                        "SalesQM.qm", "queryModel { version: 1 }",
                        "SalesTM.tm", "tableModel { version: 1 }"
                )));
        RuntimeBundlePivotOuterCacheModelIdentityProvider sameProvider =
                new RuntimeBundlePivotOuterCacheModelIdentityProvider(bundleContext(Map.of(
                        "SalesQM.qm", "queryModel { version: 1 }",
                        "SalesTM.tm", "tableModel { version: 1 }"
                )));
        RuntimeBundlePivotOuterCacheModelIdentityProvider changedTableProvider =
                new RuntimeBundlePivotOuterCacheModelIdentityProvider(bundleContext(Map.of(
                        "SalesQM.qm", "queryModel { version: 1 }",
                        "SalesTM.tm", "tableModel { version: 2 }"
                )));

        PivotOuterCacheModelIdentity first = firstProvider.resolve("", "SalesQM", queryModel);
        PivotOuterCacheModelIdentity same = sameProvider.resolve("", "SalesQM", queryModel);
        PivotOuterCacheModelIdentity changedTable = changedTableProvider.resolve("", "SalesQM", queryModel);

        assertTrue(first.bundleFingerprint().startsWith("runtime-bundle:"));
        assertEquals(first.bundleFingerprint(), same.bundleFingerprint());
        assertNotEquals(first.bundleFingerprint(), changedTable.bundleFingerprint());
    }

    @Test
    @DisplayName("local invalidation broadcaster delegates to semantic service")
    void testLocalInvalidationBroadcasterDelegatesToSemanticService() {
        SemanticQueryServiceV3 service = (SemanticQueryServiceV3) Proxy.newProxyInstance(
                SemanticQueryServiceV3.class.getClassLoader(),
                new Class<?>[]{SemanticQueryServiceV3.class},
                (proxy, method, args) -> {
                    if ("evictPivotOuterCache".equals(method.getName())) {
                        assertEquals("ns-a", args[0]);
                        assertEquals("SalesQM", args[1]);
                        return 7;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        LocalPivotOuterCacheInvalidationBroadcaster broadcaster =
                new LocalPivotOuterCacheInvalidationBroadcaster(provider(service));

        assertEquals(7, broadcaster.evict("ns-a", "SalesQM"));
    }

    @Test
    @DisplayName("local invalidation broadcaster returns zero when service is unavailable")
    void testLocalInvalidationBroadcasterReturnsZeroWithoutService() {
        LocalPivotOuterCacheInvalidationBroadcaster broadcaster =
                new LocalPivotOuterCacheInvalidationBroadcaster(provider(null));

        assertEquals(0, broadcaster.evict("ns-a", "SalesQM"));
    }

    private SystemBundlesContext bundleContext(Map<String, String> resources) {
        Map<String, BundleResource> mapped = new HashMap<>();
        Bundle bundle = bundle();
        resources.forEach((name, content) -> mapped.put(name,
                new BundleResource(bundle, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public String getDescription() {
                        return "memory:" + name;
                    }
                })));
        return (SystemBundlesContext) Proxy.newProxyInstance(
                SystemBundlesContext.class.getClassLoader(),
                new Class<?>[]{SystemBundlesContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findResourceByName" -> mapped.get(String.valueOf(args[0]));
                    case "isReady", "containBundle", "addExternalBundle", "removeBundle" -> false;
                    case "getBundleList", "listExternalBundles" -> List.of();
                    case "toString" -> "TestSystemBundlesContext";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private Bundle bundle() {
        return (Bundle) Proxy.newProxyInstance(
                Bundle.class.getClassLoader(),
                new Class<?>[]{Bundle.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "test-bundle";
                    case "getRootPath" -> "/test-bundle";
                    case "getMode" -> 0;
                    case "toString" -> "TestBundle";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private QueryModel queryModel(String name, String tableModelName) {
        TableModel tableModel = (TableModel) Proxy.newProxyInstance(
                TableModel.class.getClassLoader(),
                new Class<?>[]{TableModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> tableModelName;
                    case "toString" -> "TableModel(" + tableModelName + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (QueryModel) Proxy.newProxyInstance(
                QueryModel.class.getClassLoader(),
                new Class<?>[]{QueryModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getJdbcModel" -> tableModel;
                    case "getJdbcModelList" -> List.of(tableModel);
                    case "toString" -> "QueryModel(" + name + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private ObjectProvider<SemanticQueryServiceV3> provider(SemanticQueryServiceV3 service) {
        return new ObjectProvider<>() {
            @Override
            public SemanticQueryServiceV3 getObject(Object... args) {
                return service;
            }

            @Override
            public SemanticQueryServiceV3 getIfAvailable() {
                return service;
            }

            @Override
            public SemanticQueryServiceV3 getIfUnique() {
                return service;
            }

            @Override
            public SemanticQueryServiceV3 getObject() {
                return service;
            }
        };
    }
}
