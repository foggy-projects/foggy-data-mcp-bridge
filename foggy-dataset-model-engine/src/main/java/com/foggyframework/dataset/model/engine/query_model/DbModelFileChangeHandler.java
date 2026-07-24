package com.foggyframework.dataset.model.engine.query_model;

import com.foggyframework.dataset.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;


@Slf4j
public class DbModelFileChangeHandler implements ApplicationListener<FsscriptRemoveEvent> {

    private static final String UNKNOWN_SCOPE_DIAGNOSTIC =
            "REFRESH_SCOPE_UNKNOWN: committed file mutation scope is not provable";

    private final QueryModelLoaderImpl jdbcQueryModelLoader;
    private final TableModelLoaderManagerImpl jdbcModelLoader;
    private final CatalogRefreshCoordinator refreshCoordinator;
    private final CatalogSnapshotStore catalogSnapshotStore;

    public DbModelFileChangeHandler(
            QueryModelLoaderImpl jdbcQueryModelLoader,
            TableModelLoaderManagerImpl jdbcModelLoader,
            CatalogRefreshCoordinator refreshCoordinator
    ) {
        this.jdbcQueryModelLoader = jdbcQueryModelLoader;
        this.jdbcModelLoader = jdbcModelLoader;
        this.refreshCoordinator = refreshCoordinator;
        this.catalogSnapshotStore = jdbcModelLoader.getCatalogSnapshotStore();
        jdbcQueryModelLoader.setFileChangeHandler(this);
        jdbcModelLoader.setFileChangeHandler(this);
    }

    @Override
    public void onApplicationEvent(FsscriptRemoveEvent fsscriptRemoveEvent) {
        if (!fsscriptRemoveEvent.isScopeKnown()
                || fsscriptRemoveEvent.getAffectedNamespaces().isEmpty()) {
            Set<String> blocked = catalogSnapshotStore
                    .markKnownNamespacesStaleAdmissionBlocked(
                            UNKNOWN_SCOPE_DIAGNOSTIC);
            log.warn("Committed file mutation has unknown catalog scope; "
                            + "blocked {} known namespace admissions",
                    blocked.size());
            return;
        }

        Set<CatalogModelKey> targets = modelTargets(
                fsscriptRemoveEvent.getAffectedResources());
        for (String namespace : new TreeSet<>(
                fsscriptRemoveEvent.getAffectedNamespaces())) {
            CatalogRefreshRequest request = targets.isEmpty()
                    ? CatalogRefreshRequest.namespace(
                    namespace, CatalogRefreshTrigger.FILE)
                    : CatalogRefreshRequest.models(
                    namespace, targets, CatalogRefreshTrigger.FILE);
            try {
                refreshCoordinator.refresh(request);
            } catch (RuntimeException failure) {
                // The coordinator has already retained either an admissible old
                // snapshot or a fail-closed diagnostic snapshot. Source commit
                // is authoritative and must not be rolled back by a listener.
                log.error("Catalog refresh failed after committed file mutation: "
                                + "namespace={}, scope={}, reason={}",
                        namespace, request.scope(),
                        failure.getClass().getSimpleName());
            }
        }
    }

    private static Set<CatalogModelKey> modelTargets(
            Collection<String> affectedResources
    ) {
        TreeSet<CatalogModelKey> targets = new TreeSet<>();
        if (affectedResources == null) {
            return targets;
        }
        for (String resource : affectedResources) {
            if (resource == null || resource.isBlank()) {
                continue;
            }
            String normalized = resource.replace('\\', '/');
            int slash = normalized.lastIndexOf('/');
            String fileName = slash >= 0
                    ? normalized.substring(slash + 1)
                    : normalized;
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".tm") && fileName.length() > 3) {
                targets.add(CatalogModelKey.table(
                        fileName.substring(0, fileName.length() - 3)));
            } else if (lower.endsWith(".qm") && fileName.length() > 3) {
                targets.add(CatalogModelKey.query(
                        fileName.substring(0, fileName.length() - 3)));
            }
        }
        return Set.copyOf(targets);
    }
}
