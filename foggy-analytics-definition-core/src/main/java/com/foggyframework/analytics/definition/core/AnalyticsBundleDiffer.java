package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsQueryRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Computes deterministic typed changes without introducing product ownership metadata. */
public final class AnalyticsBundleDiffer {

    public AnalyticsBundleDiff diff(
            AnalyticsBundleIndex before,
            AnalyticsBundleIndex after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        if (!before.manifest().bundleRef().equals(after.manifest().bundleRef())) {
            throw new IllegalArgumentException("Analytics Bundle diff requires the same bundleRef");
        }

        List<AnalyticsDefinitionChange> changes = new ArrayList<>();
        compare(
                AnalyticsDefinitionType.MODEL_DEPENDENCY,
                dependencies(before),
                dependencies(after),
                ModelDependencyKey::display,
                changes);
        compare(
                AnalyticsDefinitionType.QUERY,
                before.queries(),
                after.queries(),
                AnalyticsQueryRef::value,
                changes);
        compare(
                AnalyticsDefinitionType.REPORT,
                before.reports(),
                after.reports(),
                AnalyticsBundleDiffer::artifactValue,
                changes);
        compare(
                AnalyticsDefinitionType.DASHBOARD,
                before.dashboards(),
                after.dashboards(),
                AnalyticsBundleDiffer::artifactValue,
                changes);
        changes.sort(Comparator
                .comparing(AnalyticsDefinitionChange::definitionType)
                .thenComparing(AnalyticsDefinitionChange::definitionRef)
                .thenComparing(AnalyticsDefinitionChange::changeType));

        return new AnalyticsBundleDiff(
                before.manifest().bundleRef(),
                before.bundleRevision(),
                after.bundleRevision(),
                changes);
    }

    private static Map<ModelDependencyKey, AnalyticsModelDependency> dependencies(
            AnalyticsBundleIndex index) {
        Map<ModelDependencyKey, AnalyticsModelDependency> dependencies = new LinkedHashMap<>();
        for (AnalyticsModelDependency dependency : index.manifest().modelDependencies()) {
            dependencies.put(new ModelDependencyKey(
                    dependency.namespace().value(),
                    dependency.modelKind(),
                    dependency.modelName()), dependency);
        }
        return dependencies;
    }

    private static <K, V> void compare(
            AnalyticsDefinitionType definitionType,
            Map<K, V> before,
            Map<K, V> after,
            Function<K, String> display,
            List<AnalyticsDefinitionChange> changes) {
        Set<K> identities = new LinkedHashSet<>();
        identities.addAll(before.keySet());
        identities.addAll(after.keySet());
        for (K identity : identities.stream()
                .sorted(Comparator.comparing(display))
                .toList()) {
            V previous = before.get(identity);
            V current = after.get(identity);
            AnalyticsDefinitionChangeType changeType;
            if (previous == null) {
                changeType = AnalyticsDefinitionChangeType.ADDED;
            } else if (current == null) {
                changeType = AnalyticsDefinitionChangeType.REMOVED;
            } else if (!previous.equals(current)) {
                changeType = AnalyticsDefinitionChangeType.MODIFIED;
            } else {
                continue;
            }
            changes.add(new AnalyticsDefinitionChange(
                    definitionType,
                    display.apply(identity),
                    changeType));
        }
    }

    private static String artifactValue(AnalyticsArtifactRef artifactRef) {
        return artifactRef.value();
    }

    private record ModelDependencyKey(
            String namespace,
            String modelKind,
            String modelName) {

        String display() {
            return segment(namespace) + "/" + segment(modelKind) + "/" + segment(modelName);
        }

        private static String segment(String value) {
            return value.replace("%", "%25").replace("/", "%2F");
        }
    }
}
