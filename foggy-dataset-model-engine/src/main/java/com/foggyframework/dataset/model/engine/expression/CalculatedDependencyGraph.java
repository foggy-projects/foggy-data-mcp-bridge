package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.WindowOrderDef;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.*;

/** Immutable semantic dependencies. Does not cache permissions or retain mutable request DTOs. */
public final class CalculatedDependencyGraph {
    private record Definition(String name, String expression, List<String> partition, List<String> order) {
        static Definition capture(CalculatedFieldDef field) {
            List<String> partition = field.getPartitionBy() == null ? List.of()
                    : field.getPartitionBy().stream().filter(Objects::nonNull).toList();
            List<String> order = field.getWindowOrderBy() == null ? List.of()
                    : field.getWindowOrderBy().stream().map(WindowOrderDef::getField).filter(Objects::nonNull).toList();
            return new Definition(field.getName(), field.getExpression(), partition, order);
        }
        Set<String> references() {
            var field = new CalculatedFieldDef(name, expression);
            field.setPartitionBy(partition);
            field.setWindowOrderBy(order.stream().map(f -> new WindowOrderDef(f, "ASC")).toList());
            return Collections.unmodifiableSet(new LinkedHashSet<>(CalculatedFieldDependencies.direct(field)));
        }
    }

    private final Map<String, Definition> definitions;
    private final Map<String, Set<String>> direct;
    private final Map<String, Set<String>> base;
    private final Map<String, String> errors;
    private final List<String> topologicalOrder;

    private CalculatedDependencyGraph(Map<String, Definition> definitions,
                                     Map<String, Set<String>> direct, boolean validate) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        this.direct = Collections.unmodifiableMap(new LinkedHashMap<>(direct));
        Map<String, Set<String>> resolved = new LinkedHashMap<>();
        Map<String, String> failures = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        for (String field : definitions.keySet()) {
            try { resolve(field, direct, resolved, failures, new LinkedHashSet<>(), order); }
            catch (IllegalArgumentException failure) { /* Failed paths remain fail-closed when referenced. */ }
        }
        this.base = Collections.unmodifiableMap(resolved);
        this.errors = Collections.unmodifiableMap(failures);
        this.topologicalOrder = List.copyOf(order);
        if (validate && !errors.isEmpty()) throw new IllegalArgumentException(errors.values().iterator().next());
    }

    private static Set<String> resolve(String field, Map<String, Set<String>> direct,
                                       Map<String, Set<String>> resolved, Map<String, String> failures,
                                       Set<String> path, List<String> order) {
        if (!direct.containsKey(field)) return Set.of(field);
        if (resolved.containsKey(field)) return resolved.get(field);
        if (failures.containsKey(field)) throw new IllegalArgumentException(failures.get(field));
        if (!path.add(field)) throw new IllegalArgumentException("Calculated field dependency cycle: " + path + " -> " + field);
        try {
            Set<String> refs = new LinkedHashSet<>();
            for (String dependency : direct.get(field)) refs.addAll(resolve(dependency, direct, resolved, failures, path, order));
            Set<String> value = Collections.unmodifiableSet(refs);
            resolved.put(field, value);
            order.add(field);
            return value;
        } catch (IllegalArgumentException failure) {
            failures.put(field, failure.getMessage());
            throw failure;
        } finally { path.remove(field); }
    }

    private static Map<String, Definition> capture(List<CalculatedFieldDef> fields) {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        if (fields != null) for (var field : fields) {
            if (field != null && field.getName() != null) definitions.put(field.getName(), Definition.capture(field));
        }
        return definitions;
    }

    public static CalculatedDependencyGraph compile(List<CalculatedFieldDef> fields) {
        return recompileIfChanged(null, fields);
    }

    /** Compare semantic snapshots so existing mutable Java DTO callers cannot leave a stale graph. */
    public static CalculatedDependencyGraph recompileIfChanged(CalculatedDependencyGraph previous,
                                                               List<CalculatedFieldDef> fields) {
        var definitions = capture(fields);
        if (previous != null && previous.definitions.equals(definitions)) return previous;
        Map<String, Set<String>> direct = new LinkedHashMap<>();
        definitions.forEach((name, definition) -> direct.put(name, definition.references()));
        return new CalculatedDependencyGraph(definitions, direct, true);
    }

    public static CalculatedDependencyGraph forModel(QueryModel model) {
        if (model instanceof QueryModelSupport support) return support.getCalculatedDependencyGraph();
        return compile(model == null ? List.of() : model.getPredefinedCalculatedFields());
    }

    public static CalculatedDependencyGraph forQuery(QueryModel model, List<CalculatedFieldDef> requested) {
        return forModel(model).withRequest(requested);
    }

    public CalculatedDependencyGraph withRequest(List<CalculatedFieldDef> requested) {
        var added = capture(requested);
        // Model formulas win, matching predefined injection and permission resolution.
        definitions.keySet().forEach(added::remove);
        if (added.isEmpty()) return this;
        Map<String, Definition> combined = new LinkedHashMap<>(definitions);
        combined.putAll(added);
        Map<String, Set<String>> edges = new LinkedHashMap<>(direct);
        added.forEach((name, definition) -> edges.put(name, definition.references()));
        // Injection only needs edges. Inline AS may temporarily self-reference a source column;
        // reject cycles when expanding permission dependencies, preserving later SQL-stage validation.
        return new CalculatedDependencyGraph(combined, edges, false);
    }

    public boolean containsKey(String name) { return definitions.containsKey(name); }
    public List<String> topologicalOrder() { return topologicalOrder; }

    public Set<String> base(String expression) {
        if (definitions.containsKey(expression)) return baseField(expression);
        return expand(CalculatedFieldDependencies.direct(new CalculatedFieldDef("dependency", expression)));
    }

    public Set<String> base(CalculatedFieldDef field) {
        var snapshot = Definition.capture(field);
        if (snapshot.equals(definitions.get(field.getName()))) return baseField(field.getName());
        return expand(snapshot.references());
    }

    private Set<String> baseField(String field) {
        if (errors.containsKey(field)) throw new IllegalArgumentException(errors.get(field));
        return definitions.containsKey(field) ? base.get(field) : Set.of(field);
    }

    private Set<String> expand(Set<String> refs) {
        Set<String> result = new LinkedHashSet<>();
        for (String ref : refs) result.addAll(baseField(ref));
        return Collections.unmodifiableSet(result);
    }

    public void includeDependencies(Set<String> required) {
        Deque<String> pending = new ArrayDeque<>(required);
        while (!pending.isEmpty()) {
            for (String ref : direct.getOrDefault(pending.removeFirst(), Set.of())) {
                if (required.add(ref)) pending.addLast(ref);
            }
        }
    }
}
