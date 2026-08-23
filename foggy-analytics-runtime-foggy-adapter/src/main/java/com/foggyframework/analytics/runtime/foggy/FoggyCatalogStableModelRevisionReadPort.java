package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.dataset.model.candidate.CandidateContentRevision;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.fsscript.parser.spi.FsscriptSourceContentRevision;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Stable model revision derived from one exact catalog provenance graph and its
 * compile-time-captured FSScript content closure.
 *
 * <p>The calculation deliberately excludes process-local catalog generation and
 * datasource credentials. It fails closed when the requested catalog is no
 * longer current, provenance is incomplete, or a model lacks the exact source
 * closure digest captured while that catalog was built.</p>
 */
public final class FoggyCatalogStableModelRevisionReadPort
        implements FoggyStableModelRevisionReadPort {

    static final int MAX_SOURCE_COUNT = 1_024;

    private final CatalogSnapshotStore catalogSnapshotStore;

    public FoggyCatalogStableModelRevisionReadPort(
            CatalogSnapshotStore catalogSnapshotStore) {
        this.catalogSnapshotStore = Objects.requireNonNull(
                catalogSnapshotStore,
                "catalogSnapshotStore");
    }

    @Override
    public Optional<AnalyticsModelRevision> findRevision(FoggyModelRevisionLookup lookup) {
        Objects.requireNonNull(lookup, "lookup");
        try {
            CatalogSnapshot snapshot = exactCurrentSnapshot(lookup).orElse(null);
            if (snapshot == null) {
                return Optional.empty();
            }
            ModelProvenance root = rootProvenance(snapshot, lookup).orElse(null);
            if (root == null) {
                return Optional.empty();
            }

            List<ModelProvenance> modelClosure = modelClosure(snapshot, root);
            Map<String, byte[]> revisionEntries = revisionEntries(
                    modelClosure,
                    snapshot.identity().namespace());

            CatalogSnapshot after = exactCurrentSnapshot(lookup).orElse(null);
            if (after != snapshot) {
                return Optional.empty();
            }
            return Optional.of(new AnalyticsModelRevision(
                    CandidateContentRevision.calculate(revisionEntries)));
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private Optional<CatalogSnapshot> exactCurrentSnapshot(
            FoggyModelRevisionLookup lookup) {
        return catalogSnapshotStore.readCurrent(
                        lookup.catalogIdentity().namespace())
                .filter(snapshot -> lookup.catalogIdentity().equals(snapshot.identity()));
    }

    private static Optional<ModelProvenance> rootProvenance(
            CatalogSnapshot snapshot,
            FoggyModelRevisionLookup lookup) {
        if ("tm".equals(lookup.modelKind())) {
            return snapshot.modelProvenance(
                    CatalogModelKey.table(lookup.canonicalModelName()));
        }
        return snapshot.queryModelProvenance(lookup.canonicalModelName());
    }

    private static List<ModelProvenance> modelClosure(
            CatalogSnapshot snapshot,
            ModelProvenance root) {
        LinkedHashSet<CatalogModelKey> pending = new LinkedHashSet<>();
        Set<CatalogModelKey> visited = new LinkedHashSet<>();
        pending.add(root.key());
        while (!pending.isEmpty()) {
            CatalogModelKey next = pending.iterator().next();
            pending.remove(next);
            if (!visited.add(next)) {
                continue;
            }
            ModelProvenance provenance = snapshot.provenance().get(next);
            if (provenance == null) {
                throw new IllegalStateException("catalog provenance closure is incomplete");
            }
            provenance.modelDependencies().stream()
                    .sorted()
                    .forEach(pending::add);
            if (visited.size() + pending.size() > MAX_SOURCE_COUNT) {
                throw new IllegalStateException("model provenance closure is too large");
            }
        }
        return visited.stream()
                .sorted()
                .map(snapshot.provenance()::get)
                .toList();
    }

    private static Map<String, byte[]> revisionEntries(
            List<ModelProvenance> modelClosure,
            String expectedNamespace) {
        Map<String, byte[]> entries = new TreeMap<>();

        for (int modelIndex = 0; modelIndex < modelClosure.size(); modelIndex++) {
            ModelProvenance provenance = modelClosure.get(modelIndex);
            ModelProvenance.ModelSource source = provenance.source();
            if (source == null || !expectedNamespace.equals(source.namespace())) {
                throw new IllegalStateException("model source provenance is unavailable");
            }
            String sourceClosureRevision = source.sourceClosureRevision();
            if (!FsscriptSourceContentRevision.isCanonical(sourceClosureRevision)) {
                throw new IllegalStateException(
                        "compiled FSScript source closure revision is unavailable");
            }

            String modelPrefix = "model/" + modelIndex;
            entries.put(modelPrefix + "/identity", modelIdentity(provenance));
            entries.put(modelPrefix + "/dependencies", modelDependencies(provenance));
            entries.put(
                    modelPrefix + "/source-closure",
                    sourceClosureRevision.getBytes(StandardCharsets.UTF_8));
        }
        return entries;
    }

    private static byte[] modelIdentity(ModelProvenance provenance) {
        return (provenance.kind().name() + '\0' + provenance.canonicalName())
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] modelDependencies(ModelProvenance provenance) {
        String value = provenance.modelDependencies().stream()
                .sorted()
                .map(key -> key.kind().name() + "\0" + key.canonicalName())
                .reduce((left, right) -> left + "\0" + right)
                .orElse("");
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
