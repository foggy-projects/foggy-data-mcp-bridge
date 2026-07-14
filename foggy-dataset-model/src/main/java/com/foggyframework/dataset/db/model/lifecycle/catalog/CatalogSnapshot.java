package com.foggyframework.dataset.db.model.lifecycle.catalog;

import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** One immutable, atomically published namespace catalog. */
public record CatalogSnapshot(
        CatalogIdentity identity,
        Map<String, TableModel> tableModels,
        Map<String, QueryModel> queryModels,
        Map<String, QueryModel> syntheticQueryModels,
        Set<String> discoveredQueryModelNames,
        Map<String, String> canonicalToAlias,
        Map<String, String> aliasToCanonical,
        Map<CatalogModelKey, ModelProvenance> provenance
) {
    public CatalogSnapshot {
        if (identity == null) {
            throw new IllegalArgumentException("identity must not be null");
        }
        tableModels = immutableSortedMap(tableModels);
        queryModels = immutableSortedMap(queryModels);
        syntheticQueryModels = immutableSortedMap(syntheticQueryModels);
        discoveredQueryModelNames = Collections.unmodifiableSet(new LinkedHashSet<>(
                new TreeSet<>(discoveredQueryModelNames == null ? Set.of() : discoveredQueryModelNames)));
        canonicalToAlias = immutableSortedMap(canonicalToAlias);
        aliasToCanonical = immutableSortedMap(aliasToCanonical);
        provenance = immutableSortedMap(provenance);
        validateAliasBijection(canonicalToAlias, aliasToCanonical);
        validateCatalogIntegrity(identity, tableModels, queryModels, syntheticQueryModels,
                discoveredQueryModelNames, canonicalToAlias, provenance);
    }

    public Optional<QueryModel> resolveQueryModel(String nameOrAlias) {
        if (nameOrAlias == null || nameOrAlias.isBlank()) {
            return Optional.empty();
        }
        String canonical = aliasToCanonical.getOrDefault(nameOrAlias, nameOrAlias);
        QueryModel model = queryModels.get(canonical);
        if (model == null) {
            model = syntheticQueryModels.get(canonical);
        }
        return Optional.ofNullable(model);
    }

    public String canonicalQueryModelName(String nameOrAlias) {
        return aliasToCanonical.getOrDefault(nameOrAlias, nameOrAlias);
    }

    public Optional<ModelProvenance> modelProvenance(CatalogModelKey key) {
        return Optional.ofNullable(provenance.get(key));
    }

    public Optional<ModelProvenance> queryModelProvenance(String canonicalName) {
        ModelProvenance normal = provenance.get(CatalogModelKey.query(canonicalName));
        return Optional.ofNullable(normal != null
                ? normal
                : provenance.get(CatalogModelKey.syntheticQuery(canonicalName)));
    }

    private static <K extends Comparable<? super K>, V> Map<K, V> immutableSortedMap(Map<K, V> source) {
        TreeMap<K, V> sorted = new TreeMap<>();
        if (source != null) {
            sorted.putAll(source);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static void validateAliasBijection(
            Map<String, String> canonicalToAlias,
            Map<String, String> aliasToCanonical
    ) {
        if (canonicalToAlias.size() != aliasToCanonical.size()) {
            throw new IllegalArgumentException("catalog alias indexes are not a bijection");
        }
        canonicalToAlias.forEach((canonical, alias) -> {
            if (!canonical.equals(aliasToCanonical.get(alias))) {
                throw new IllegalArgumentException("catalog alias indexes disagree for " + canonical);
            }
        });
    }

    private static void validateCatalogIntegrity(
            CatalogIdentity identity,
            Map<String, TableModel> tableModels,
            Map<String, QueryModel> queryModels,
            Map<String, QueryModel> syntheticQueryModels,
            Set<String> discoveredQueryModelNames,
            Map<String, String> canonicalToAlias,
            Map<CatalogModelKey, ModelProvenance> provenance
    ) {
        Set<String> overlap = new TreeSet<>(queryModels.keySet());
        overlap.retainAll(syntheticQueryModels.keySet());
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("normal and synthetic QM slots overlap: " + overlap);
        }
        if (!canonicalToAlias.keySet().equals(discoveredQueryModelNames)) {
            throw new IllegalArgumentException("alias plan must cover exactly the discovery set");
        }
        if (!discoveredQueryModelNames.containsAll(queryModels.keySet())
                || !discoveredQueryModelNames.containsAll(syntheticQueryModels.keySet())) {
            throw new IllegalArgumentException("published query models must belong to discovery");
        }
        tableModels.forEach((canonical, model) -> {
            if (model == null || !canonical.equals(model.getName())) {
                throw new IllegalArgumentException("table model slot/name mismatch: " + canonical);
            }
        });
        queryModels.forEach((canonical, model) ->
                validateQueryModelSlot(canonical, model, canonicalToAlias));
        syntheticQueryModels.forEach((canonical, model) ->
                validateQueryModelSlot(canonical, model, canonicalToAlias));

        Set<CatalogModelKey> expectedProvenance = new TreeSet<>();
        tableModels.keySet().forEach(name -> expectedProvenance.add(CatalogModelKey.table(name)));
        queryModels.keySet().forEach(name -> expectedProvenance.add(CatalogModelKey.query(name)));
        syntheticQueryModels.keySet().forEach(
                name -> expectedProvenance.add(CatalogModelKey.syntheticQuery(name)));
        if (!expectedProvenance.equals(provenance.keySet())) {
            throw new IllegalArgumentException("catalog provenance does not match model slots");
        }
        provenance.forEach((key, value) -> {
            if (value == null || !key.equals(value.key())) {
                throw new IllegalArgumentException("catalog provenance key/value mismatch: " + key);
            }
            if (!identity.sourceRevision().equals(value.sourceRevision())) {
                throw new IllegalArgumentException("catalog provenance source revision mismatch: " + key);
            }
            if (!provenance.keySet().containsAll(value.modelDependencies())) {
                throw new IllegalArgumentException("catalog provenance has missing dependencies: " + key);
            }
        });
        validateAcyclicProvenance(provenance);
    }

    /**
     * The immutable snapshot is the final fail-closed boundary for dependency
     * graphs. Normal loaders reject cycles before waiting on a flight, but a
     * custom loader must not be able to publish a graph that bypasses that
     * guard.
     */
    private static void validateAcyclicProvenance(
            Map<CatalogModelKey, ModelProvenance> provenance
    ) {
        Map<CatalogModelKey, VisitState> states = new HashMap<>();
        Deque<CatalogModelKey> path = new ArrayDeque<>();
        for (CatalogModelKey key : provenance.keySet()) {
            visit(key, provenance, states, path);
        }
    }

    private static void visit(
            CatalogModelKey key,
            Map<CatalogModelKey, ModelProvenance> provenance,
            Map<CatalogModelKey, VisitState> states,
            Deque<CatalogModelKey> path
    ) {
        VisitState state = states.get(key);
        if (state == VisitState.VISITED) {
            return;
        }
        if (state == VisitState.VISITING) {
            StringBuilder cycle = new StringBuilder();
            boolean include = false;
            for (CatalogModelKey element : path) {
                include |= element.equals(key);
                if (include) {
                    if (cycle.length() > 0) {
                        cycle.append(" -> ");
                    }
                    cycle.append(element);
                }
            }
            if (cycle.length() > 0) {
                cycle.append(" -> ");
            }
            cycle.append(key);
            throw new IllegalArgumentException(
                    "MODEL_BUILD_DEPENDENCY_CYCLE: " + cycle);
        }

        states.put(key, VisitState.VISITING);
        path.addLast(key);
        for (CatalogModelKey dependency : provenance.get(key).modelDependencies()) {
            visit(dependency, provenance, states, path);
        }
        path.removeLast();
        states.put(key, VisitState.VISITED);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    private static void validateQueryModelSlot(
            String canonical,
            QueryModel model,
            Map<String, String> canonicalToAlias
    ) {
        if (model == null || !canonical.equals(model.getName())) {
            throw new IllegalArgumentException("query model slot/name mismatch: " + canonical);
        }
        if (!Objects.equals(canonicalToAlias.get(canonical), model.getShortAlias())) {
            throw new IllegalArgumentException("query model slot/alias mismatch: " + canonical);
        }
    }
}
