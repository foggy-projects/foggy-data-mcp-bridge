package com.foggyframework.dataset.model.lifecycle.catalog;

import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshPlan;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshScope;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.TableModel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Request-local mutable staging area; never exposed to catalog consumers. */
public final class CatalogCandidate {

    private static final Pattern CAMEL_WORD = Pattern.compile("[A-Z][a-z0-9]*");

    private final String namespace;
    private final SourceRevision sourceRevision;
    private final CatalogSnapshot base;
    private final Thread ownerThread;
    private final Map<String, TableModel> tableModels = new LinkedHashMap<>();
    private final Map<String, QueryModel> queryModels = new LinkedHashMap<>();
    private final Map<String, QueryModel> syntheticQueryModels = new LinkedHashMap<>();
    private final Set<String> discoveredQueryModelNames = new LinkedHashSet<>();
    private final Map<String, String> canonicalToAlias = new LinkedHashMap<>();
    private final Map<String, String> aliasToCanonical = new LinkedHashMap<>();
    private final Map<CatalogModelKey, ModelProvenance> provenance = new LinkedHashMap<>();
    private final List<String> buildFailures = new ArrayList<>();
    private boolean changed;
    private boolean sealed;

    CatalogCandidate(String namespace, SourceRevision sourceRevision, CatalogSnapshot base) {
        this.namespace = CatalogIdentity.canonicalNamespace(namespace);
        this.sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision");
        this.base = base;
        this.ownerThread = Thread.currentThread();
        if (base != null) {
            if (!this.namespace.equals(base.identity().namespace())) {
                throw new IllegalArgumentException("candidate/base namespace mismatch");
            }
            tableModels.putAll(base.tableModels());
            queryModels.putAll(base.queryModels());
            syntheticQueryModels.putAll(base.syntheticQueryModels());
            discoveredQueryModelNames.addAll(base.discoveredQueryModelNames());
            canonicalToAlias.putAll(base.canonicalToAlias());
            aliasToCanonical.putAll(base.aliasToCanonical());
            provenance.putAll(base.provenance());
        }
    }

    public String namespace() {
        return namespace;
    }

    public SourceRevision sourceRevision() {
        return sourceRevision;
    }

    public CatalogSnapshot base() {
        return base;
    }

    public TableModel findTableModel(String canonicalName) {
        ensureOwnerThread();
        return tableModels.get(canonicalName);
    }

    public QueryModel findQueryModel(String nameOrAlias) {
        ensureOwnerThread();
        String canonical = resolveCanonicalName(nameOrAlias);
        QueryModel model = queryModels.get(canonical);
        return model != null ? model : syntheticQueryModels.get(canonical);
    }

    public String resolveCanonicalName(String nameOrAlias) {
        ensureOwnerThread();
        return aliasToCanonical.getOrDefault(nameOrAlias, nameOrAlias);
    }

    public ModelProvenance modelProvenance(CatalogModelKey key) {
        ensureOwnerThread();
        return provenance.get(key);
    }

    public ModelProvenance modelProvenance(
            ModelProvenance.ModelKind kind,
            String canonicalName
    ) {
        ensureOwnerThread();
        return provenance.get(new CatalogModelKey(kind, canonicalName));
    }

    /**
     * Applies the destructive part of a refresh plan to this detached
     * candidate. Nothing becomes visible until the owning scope commits.
     *
     * @return the canonical slots that the refresh callback may need to stage
     */
    public Set<CatalogModelKey> applyRefreshPlan(CatalogRefreshPlan plan) {
        ensureMutable();
        Objects.requireNonNull(plan, "plan");
        if (plan.scope() == CatalogRefreshScope.NAMESPACE) {
            return resetForNamespaceRefresh(plan.discoveredQueryModelNames());
        }
        return invalidateForModelRefresh(
                plan.targets(), plan.discoveredQueryModelNames());
    }

    /**
     * Starts a full namespace rebuild from an exact committed discovery set.
     * The old candidate contents are discarded, but the active snapshot is
     * retained by the store until this candidate has completely validated.
     */
    public Set<CatalogModelKey> resetForNamespaceRefresh(
            Collection<String> exactDiscoveredQueryModelNames
    ) {
        ensureMutable();
        Set<CatalogModelKey> previous = immutableModelKeys(provenance.keySet());
        tableModels.clear();
        queryModels.clear();
        syntheticQueryModels.clear();
        provenance.clear();
        canonicalToAlias.clear();
        aliasToCanonical.clear();
        replaceDiscovery(exactDiscoveredQueryModelNames);
        rebuildAliasPlan();

        // A successful explicit refresh is itself observable, including an
        // empty cold namespace and a byte-identical source rebuild.
        changed = true;
        return previous;
    }

    /**
     * Invalidates requested slots, their dependency closure and every reverse
     * dependent while retaining unrelated sibling objects. Normal query-model
     * discovery is exact; dynamic synthetic names survive only while their
     * dependency graph and deterministic alias remain unchanged.
     */
    public Set<CatalogModelKey> invalidateForModelRefresh(
            Collection<CatalogModelKey> targets,
            Collection<String> exactDiscoveredQueryModelNames
    ) {
        ensureMutable();
        TreeSet<CatalogModelKey> closure = new TreeSet<>();
        if (targets != null) {
            targets.forEach(target ->
                    closure.add(Objects.requireNonNull(target, "target")));
        }
        if (closure.isEmpty()) {
            throw new IllegalArgumentException(
                    "model refresh requires at least one canonical target");
        }

        Set<String> exactDiscovery = canonicalNames(
                exactDiscoveredQueryModelNames);
        queryModels.keySet().stream()
                .filter(name -> !exactDiscovery.contains(name))
                .map(CatalogModelKey::query)
                .forEach(closure::add);

        expandDependencyClosure(closure);

        // Build the alias plan for the committed normal discovery plus each
        // still-candidate synthetic slot. Alias changes require rebuilding the
        // corresponding object because aliases are embedded in QueryModel.
        TreeSet<String> nextDiscovery = new TreeSet<>(exactDiscovery);
        nextDiscovery.addAll(syntheticQueryModels.keySet());
        boolean expanded;
        do {
            nextDiscovery.removeIf(name ->
                    closure.contains(CatalogModelKey.syntheticQuery(name)));
            replaceDiscovery(nextDiscovery);
            rebuildAliasPlan();

            int sizeBeforeAliases = closure.size();
            addAliasMismatches(closure, queryModels,
                    ModelProvenance.ModelKind.QUERY);
            addAliasMismatches(closure, syntheticQueryModels,
                    ModelProvenance.ModelKind.SYNTHETIC_QUERY);
            expandDependencyClosure(closure);
            expanded = closure.size() != sizeBeforeAliases;
        } while (expanded);

        for (CatalogModelKey key : closure) {
            removeModelSlot(key);
        }
        nextDiscovery.removeIf(name ->
                closure.contains(CatalogModelKey.syntheticQuery(name)));
        replaceDiscovery(nextDiscovery);
        rebuildAliasPlan();
        rebasePreservedProvenance();

        // Explicit model refresh always publishes one new namespace
        // generation, even when a requested target was already absent.
        changed = true;
        return immutableModelKeys(closure);
    }

    /** Returns all currently staged canonical slots in deterministic order. */
    public Set<CatalogModelKey> modelKeys() {
        ensureOwnerThread();
        return immutableModelKeys(provenance.keySet());
    }

    /**
     * Returns the effective binding identity set in canonical binding-key
     * order. Conflicting identities for one logical key are rejected rather
     * than selecting an arbitrary publication guard.
     */
    public Map<String, DatasourceBindingIdentity> effectiveDatasourceBindings() {
        ensureOwnerThread();
        TreeMap<String, DatasourceBindingIdentity> effective = new TreeMap<>();
        for (ModelProvenance modelProvenance
                : new TreeMap<>(provenance).values()) {
            for (Map.Entry<String, DatasourceBindingIdentity> binding
                    : new TreeMap<>(modelProvenance.datasourceBindings()).entrySet()) {
                DatasourceBindingIdentity previous = effective.putIfAbsent(
                        binding.getKey(), binding.getValue());
                if (previous != null && !previous.equals(binding.getValue())) {
                    throw new IllegalStateException(
                            "DATASOURCE_BINDING_IDENTITY_CONFLICT: "
                                    + binding.getKey());
                }
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(effective));
    }

    /** True only when every staged model has cache-safe binding provenance. */
    public boolean bindingIdentityComplete() {
        ensureOwnerThread();
        return provenance.values().stream()
                .allMatch(ModelProvenance::bindingIdentityComplete);
    }

    /** Freeze the complete discovery set before assigning any published alias. */
    public void discoverQueryModels(Collection<String> canonicalNames) {
        ensureMutable();
        TreeSet<String> next = new TreeSet<>(discoveredQueryModelNames);
        if (canonicalNames != null) {
            canonicalNames.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .forEach(next::add);
        }
        if (!next.equals(new TreeSet<>(discoveredQueryModelNames))) {
            discoveredQueryModelNames.clear();
            discoveredQueryModelNames.addAll(next);
            changed = true;
        }
        rebuildAliasPlan();
    }

    private void expandDependencyClosure(Set<CatalogModelKey> closure) {
        boolean expanded;
        do {
            int sizeBefore = closure.size();
            for (Map.Entry<CatalogModelKey, ModelProvenance> entry
                    : new TreeMap<>(provenance).entrySet()) {
                CatalogModelKey key = entry.getKey();
                Set<CatalogModelKey> dependencies =
                        entry.getValue().modelDependencies();
                if (closure.contains(key)) {
                    closure.addAll(dependencies);
                }
                if (dependencies.stream().anyMatch(closure::contains)) {
                    closure.add(key);
                }
            }
            expanded = closure.size() != sizeBefore;
        } while (expanded);
    }

    private void addAliasMismatches(
            Set<CatalogModelKey> closure,
            Map<String, QueryModel> models,
            ModelProvenance.ModelKind kind
    ) {
        for (Map.Entry<String, QueryModel> entry : models.entrySet()) {
            String planned = canonicalToAlias.get(entry.getKey());
            if (!Objects.equals(planned, entry.getValue().getShortAlias())) {
                closure.add(new CatalogModelKey(kind, entry.getKey()));
            }
        }
    }

    private void removeModelSlot(CatalogModelKey key) {
        switch (key.kind()) {
            case TABLE -> tableModels.remove(key.canonicalName());
            case QUERY -> queryModels.remove(key.canonicalName());
            case SYNTHETIC_QUERY -> syntheticQueryModels.remove(key.canonicalName());
        }
        provenance.remove(key);
    }

    private void rebasePreservedProvenance() {
        Map<CatalogModelKey, ModelProvenance> rebased = new LinkedHashMap<>();
        new TreeMap<>(provenance).forEach((key, old) -> rebased.put(
                key,
                sourceRevision.equals(old.sourceRevision())
                        ? old
                        : new ModelProvenance(
                        old.canonicalName(),
                        old.kind(),
                        sourceRevision,
                        old.modelDependencies(),
                        old.datasourceBindings(),
                        old.bindingIdentityComplete(),
                        old.diagnostics())));
        provenance.clear();
        provenance.putAll(rebased);
    }

    private void replaceDiscovery(Collection<String> canonicalNames) {
        discoveredQueryModelNames.clear();
        discoveredQueryModelNames.addAll(canonicalNames(canonicalNames));
    }

    private Set<String> canonicalNames(Collection<String> source) {
        TreeSet<String> canonical = new TreeSet<>();
        if (source != null) {
            source.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .forEach(canonical::add);
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(canonical));
    }

    private Set<CatalogModelKey> immutableModelKeys(
            Collection<CatalogModelKey> source
    ) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(
                new TreeSet<>(source == null ? Set.of() : source)));
    }

    public String aliasFor(String canonicalName) {
        ensureMutable();
        if (!discoveredQueryModelNames.contains(canonicalName)) {
            discoverQueryModels(List.of(canonicalName));
        }
        return canonicalToAlias.get(canonicalName);
    }

    public void putTableModel(String canonicalName, TableModel model, ModelProvenance modelProvenance) {
        ensureMutable();
        requireCanonical(canonicalName);
        Objects.requireNonNull(model, "model");
        requireProvenance(canonicalName, modelProvenance, ModelProvenance.ModelKind.TABLE);
        TableModel previous = tableModels.putIfAbsent(canonicalName, model);
        if (previous != null && previous != model) {
            throw new IllegalStateException("candidate already contains table model " + canonicalName);
        }
        ModelProvenance oldProvenance = provenance.putIfAbsent(
                CatalogModelKey.table(canonicalName), modelProvenance);
        if (oldProvenance != null && !oldProvenance.equals(modelProvenance)) {
            throw new IllegalStateException("candidate provenance changed for " + canonicalName);
        }
        changed |= previous == null;
    }

    public void putQueryModel(String canonicalName, QueryModel model, ModelProvenance modelProvenance) {
        putQueryModel(canonicalName, model, modelProvenance, false);
    }

    public void putSyntheticQueryModel(
            String canonicalName,
            QueryModel model,
            ModelProvenance modelProvenance
    ) {
        putQueryModel(canonicalName, model, modelProvenance, true);
    }

    private void putQueryModel(
            String canonicalName,
            QueryModel model,
            ModelProvenance modelProvenance,
            boolean synthetic
    ) {
        ensureMutable();
        requireCanonical(canonicalName);
        Objects.requireNonNull(model, "model");
        ModelProvenance.ModelKind expectedKind = synthetic
                ? ModelProvenance.ModelKind.SYNTHETIC_QUERY
                : ModelProvenance.ModelKind.QUERY;
        requireProvenance(canonicalName, modelProvenance, expectedKind);
        String plannedAlias = aliasFor(canonicalName);
        if (model instanceof QueryModelSupport support && support.getShortAlias() == null) {
            support.setShortAlias(plannedAlias);
        }
        if (!plannedAlias.equals(model.getShortAlias())) {
            throw new IllegalStateException("query model alias does not match deterministic plan for "
                    + canonicalName);
        }
        Map<String, QueryModel> target = synthetic ? syntheticQueryModels : queryModels;
        QueryModel previous = target.putIfAbsent(canonicalName, model);
        if (previous != null && previous != model) {
            throw new IllegalStateException("candidate already contains query model " + canonicalName);
        }
        CatalogModelKey provenanceKey = synthetic
                ? CatalogModelKey.syntheticQuery(canonicalName)
                : CatalogModelKey.query(canonicalName);
        ModelProvenance oldProvenance = provenance.putIfAbsent(provenanceKey, modelProvenance);
        if (oldProvenance != null && !oldProvenance.equals(modelProvenance)) {
            throw new IllegalStateException("candidate provenance changed for " + canonicalName);
        }
        changed |= previous == null;
    }

    public void fail(String sanitizedDiagnostic) {
        ensureMutable();
        buildFailures.add(sanitizedDiagnostic == null || sanitizedDiagnostic.isBlank()
                ? "catalog candidate build failed"
                : sanitizedDiagnostic);
    }

    public boolean hasObservableChanges() {
        ensureOwnerThread();
        return changed;
    }

    /**
     * Replays only additive lazy-load changes onto a newer catalog snapshot.
     *
     * <p>This is deliberately narrower than refresh rebasing.  The captured
     * base must still be present by object identity, no captured slot may have
     * been removed or replaced, and every datasource binding introduced by
     * the newer snapshot must already be covered by this candidate's final
     * publication guard.  A namespace/model refresh therefore remains a
     * strict stale boundary, while disjoint cold lazy loads may commute.</p>
     *
     * @return a rebased candidate, or {@code null} when the newer snapshot is
     * not a safe additive extension of the captured base
     */
    CatalogCandidate rebaseAdditionsOnto(CatalogSnapshot latest) {
        ensureMutable();
        validateBuildSucceeded();
        if (latest == null
                || !namespace.equals(latest.identity().namespace())
                || !sourceRevision.equals(latest.identity().sourceRevision())
                || !preservesCapturedBase(latest)
                || !latestBindingsCoveredByCandidate(latest)) {
            return null;
        }

        CatalogCandidate rebased = new CatalogCandidate(
                namespace, sourceRevision, latest);
        rebased.discoverQueryModels(discoveredQueryModelNames);
        if (!rebased.aliasesRemainCompatible(latest)) {
            return null;
        }

        for (Map.Entry<String, TableModel> entry : tableModels.entrySet()) {
            if (isCapturedTable(entry.getKey(), entry.getValue())
                    || latest.tableModels().containsKey(entry.getKey())) {
                continue;
            }
            rebased.putTableModel(
                    entry.getKey(),
                    entry.getValue(),
                    provenance.get(CatalogModelKey.table(entry.getKey())));
        }
        for (Map.Entry<String, QueryModel> entry : queryModels.entrySet()) {
            if (isCapturedQuery(entry.getKey(), entry.getValue(), false)
                    || latest.queryModels().containsKey(entry.getKey())) {
                continue;
            }
            rebased.putQueryModel(
                    entry.getKey(),
                    entry.getValue(),
                    provenance.get(CatalogModelKey.query(entry.getKey())));
        }
        for (Map.Entry<String, QueryModel> entry : syntheticQueryModels.entrySet()) {
            if (isCapturedQuery(entry.getKey(), entry.getValue(), true)
                    || latest.syntheticQueryModels().containsKey(entry.getKey())) {
                continue;
            }
            rebased.putSyntheticQueryModel(
                    entry.getKey(),
                    entry.getValue(),
                    provenance.get(CatalogModelKey.syntheticQuery(entry.getKey())));
        }
        return rebased;
    }

    private boolean aliasesRemainCompatible(CatalogSnapshot latest) {
        return latest.queryModels().entrySet().stream().allMatch(entry ->
                Objects.equals(canonicalToAlias.get(entry.getKey()),
                        entry.getValue().getShortAlias()))
                && latest.syntheticQueryModels().entrySet().stream().allMatch(entry ->
                Objects.equals(canonicalToAlias.get(entry.getKey()),
                        entry.getValue().getShortAlias()));
    }

    private boolean preservesCapturedBase(CatalogSnapshot latest) {
        if (base == null) {
            return true;
        }
        if (!sameEntriesByIdentity(base.tableModels(), tableModels)
                || !sameEntriesByIdentity(base.queryModels(), queryModels)
                || !sameEntriesByIdentity(
                base.syntheticQueryModels(), syntheticQueryModels)
                || !base.provenance().entrySet().stream().allMatch(entry ->
                Objects.equals(provenance.get(entry.getKey()), entry.getValue()))) {
            return false;
        }
        return sameEntriesByIdentity(base.tableModels(), latest.tableModels())
                && sameEntriesByIdentity(base.queryModels(), latest.queryModels())
                && sameEntriesByIdentity(
                base.syntheticQueryModels(), latest.syntheticQueryModels())
                && base.provenance().entrySet().stream().allMatch(entry ->
                Objects.equals(latest.provenance().get(entry.getKey()), entry.getValue()));
    }

    private boolean latestBindingsCoveredByCandidate(CatalogSnapshot latest) {
        Map<String, DatasourceBindingIdentity> guarded = effectiveDatasourceBindings();
        for (ModelProvenance latestProvenance : latest.provenance().values()) {
            for (Map.Entry<String, DatasourceBindingIdentity> binding
                    : latestProvenance.datasourceBindings().entrySet()) {
                if (!binding.getValue().equals(guarded.get(binding.getKey()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isCapturedTable(String name, TableModel model) {
        return base != null && base.tableModels().get(name) == model;
    }

    private boolean isCapturedQuery(String name, QueryModel model, boolean synthetic) {
        if (base == null) {
            return false;
        }
        Map<String, QueryModel> captured = synthetic
                ? base.syntheticQueryModels()
                : base.queryModels();
        return captured.get(name) == model;
    }

    private static <T> boolean sameEntriesByIdentity(
            Map<String, T> expected,
            Map<String, T> actual
    ) {
        for (Map.Entry<String, T> entry : expected.entrySet()) {
            if (actual.get(entry.getKey()) != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    CatalogSnapshot freeze(CatalogIdentity identity) {
        validateBuildSucceeded();
        if (!namespace.equals(identity.namespace()) || !sourceRevision.equals(identity.sourceRevision())) {
            throw new IllegalStateException("catalog candidate identity does not match captured input view");
        }
        rebuildAliasPlan();
        queryModels.forEach(this::validatePublishedAlias);
        syntheticQueryModels.forEach(this::validatePublishedAlias);
        return new CatalogSnapshot(identity, tableModels, queryModels, syntheticQueryModels,
                discoveredQueryModelNames, canonicalToAlias, aliasToCanonical, provenance);
    }

    void seal() {
        ensureOwnerThread();
        sealed = true;
    }

    void validateBuildSucceeded() {
        ensureMutable();
        if (!buildFailures.isEmpty()) {
            throw new IllegalStateException("catalog candidate contains build failures: "
                    + String.join("; ", buildFailures));
        }
    }

    private void validatePublishedAlias(String canonicalName, QueryModel model) {
        if (!Objects.equals(canonicalToAlias.get(canonicalName), model.getShortAlias())) {
            throw new IllegalStateException("published query model alias changed after construction: "
                    + canonicalName);
        }
    }

    private void rebuildAliasPlan() {
        Map<String, List<String>> groups = new TreeMap<>();
        for (String canonical : new TreeSet<>(discoveredQueryModelNames)) {
            if (isDynamicSyntheticName(canonical)) {
                continue;
            }
            groups.computeIfAbsent(baseAlias(canonical), ignored -> new ArrayList<>()).add(canonical);
        }
        Set<String> reserved = new LinkedHashSet<>(discoveredQueryModelNames);
        Set<String> used = new LinkedHashSet<>();
        Map<String, String> nextCanonicalToAlias = new LinkedHashMap<>();
        Map<String, String> nextAliasToCanonical = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> group : groups.entrySet()) {
            List<String> names = group.getValue();
            names.sort(String::compareTo);
            for (int index = 0; index < names.size(); index++) {
                String alias = index == 0 ? group.getKey() : group.getKey() + (index + 1);
                int suffix = Math.max(2, index + 2);
                while (reserved.contains(alias) || !used.add(alias)) {
                    alias = group.getKey() + suffix++;
                }
                String canonical = names.get(index);
                nextCanonicalToAlias.put(canonical, alias);
                nextAliasToCanonical.put(alias, canonical);
            }
        }
        for (String canonical : new TreeSet<>(discoveredQueryModelNames)) {
            if (!isDynamicSyntheticName(canonical)) {
                continue;
            }
            String alias = stableSyntheticAlias(canonical);
            while (reserved.contains(alias) || !used.add(alias)) {
                alias = alias + "X";
            }
            nextCanonicalToAlias.put(canonical, alias);
            nextAliasToCanonical.put(alias, canonical);
        }
        if (!nextCanonicalToAlias.equals(canonicalToAlias)) {
            changed = true;
        }
        canonicalToAlias.clear();
        canonicalToAlias.putAll(nextCanonicalToAlias);
        aliasToCanonical.clear();
        aliasToCanonical.putAll(nextAliasToCanonical);
    }

    private boolean isDynamicSyntheticName(String canonicalName) {
        return canonicalName.indexOf('#') > 0;
    }

    private String stableSyntheticAlias(String canonicalName) {
        String sourceName = canonicalName.substring(0, canonicalName.indexOf('#'));
        String stableHash = UUID.nameUUIDFromBytes(
                        canonicalName.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
        return baseAlias(sourceName) + "S" + stableHash;
    }

    private String baseAlias(String canonicalName) {
        String base = canonicalName;
        if (base.endsWith("QueryModel")) {
            base = base.substring(0, base.length() - "QueryModel".length());
        } else if (base.endsWith("Model")) {
            base = base.substring(0, base.length() - "Model".length());
        }
        Matcher matcher = CAMEL_WORD.matcher(base);
        StringBuilder alias = new StringBuilder();
        while (matcher.find()) {
            alias.append(matcher.group().charAt(0));
        }
        if (alias.length() == 0) {
            String fallback = base.isEmpty() ? canonicalName : base;
            alias.append(fallback.substring(0, Math.min(2, fallback.length())).toUpperCase());
        }
        return alias.toString();
    }

    private void requireProvenance(
            String canonicalName,
            ModelProvenance modelProvenance,
            ModelProvenance.ModelKind expectedKind
    ) {
        Objects.requireNonNull(modelProvenance, "modelProvenance");
        if (!canonicalName.equals(modelProvenance.canonicalName())
                || expectedKind != modelProvenance.kind()
                || !new CatalogModelKey(expectedKind, canonicalName).equals(modelProvenance.key())
                || !sourceRevision.equals(modelProvenance.sourceRevision())) {
            throw new IllegalArgumentException("model provenance does not match candidate: " + canonicalName);
        }
    }

    private void requireCanonical(String canonicalName) {
        if (canonicalName == null || canonicalName.isBlank()) {
            throw new IllegalArgumentException("canonical model name must not be blank");
        }
    }

    private void ensureMutable() {
        ensureOwnerThread();
        if (sealed) {
            throw new IllegalStateException("catalog candidate is sealed after publication");
        }
    }

    private void ensureOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "catalog candidate must be used by its owner thread");
        }
    }
}
