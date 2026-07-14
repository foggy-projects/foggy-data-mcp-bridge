import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherConfig;

/**
 * Emits report-owning class names from a JUnit Platform discovery plan without
 * executing tests. The surrounding v934 inventory tool binds this raw output
 * to source and test-class hashes before it becomes frozen contract data.
 */
public final class JUnitDiscoveryInventory {

    private JUnitDiscoveryInventory() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: JUnitDiscoveryInventory <module> <selectors.tsv> <output.tsv>");
            System.exit(2);
        }

        String module = args[0];
        Path selectorsFile = Path.of(args[1]);
        Path outputFile = Path.of(args[2]);
        List<String> selectors = readSelectors(selectorsFile);

        LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();
        for (String selector : selectors) {
            requestBuilder.selectors(DiscoverySelectors.selectClass(selector));
        }
        LauncherDiscoveryRequest request = requestBuilder.build();
        LauncherConfig launcherConfig = LauncherConfig.builder()
                .enableTestEngineAutoRegistration(false)
                .enableLauncherSessionListenerAutoRegistration(false)
                .enableLauncherDiscoveryListenerAutoRegistration(false)
                .enableTestExecutionListenerAutoRegistration(false)
                .enablePostDiscoveryFilterAutoRegistration(false)
                .addTestEngines(new JupiterTestEngine())
                .build();
        Launcher launcher = LauncherFactory.create(launcherConfig);
        TestPlan plan = launcher.discover(request);

        Map<String, ReportDiscovery> reports = new TreeMap<>();
        for (TestIdentifier identifier : allIdentifiers(plan)) {
            Optional<String> sourceClass = sourceClass(identifier.getSource());
            if (identifier.isContainer()
                    && identifier.getSource().filter(ClassSource.class::isInstance).isPresent()) {
                String className = sourceClass.orElseThrow();
                ReportDiscovery report = reports.computeIfAbsent(className, ignored -> new ReportDiscovery());
                identifier.getUniqueIdObject().getEngineId().ifPresent(report.engines::add);
            }
            if (!identifier.isTest() && !isRuntimeDeferredContainer(identifier)) {
                continue;
            }
            if (sourceClass.isEmpty()) {
                throw new IllegalStateException(
                        "test identifier has no class-backed source: " + identifier.getUniqueId());
            }
            String className = sourceClass.get();
            ReportDiscovery report = reports.computeIfAbsent(className, ignored -> new ReportDiscovery());
            if (identifier.isTest()) {
                report.testNodes++;
            } else {
                report.runtimeDeferredContainers++;
            }
            identifier.getUniqueIdObject().getEngineId().ifPresent(report.engines::add);
        }

        Map<String, List<Map.Entry<String, ReportDiscovery>>> bySelector = new LinkedHashMap<>();
        for (String selector : selectors) {
            bySelector.put(selector, new ArrayList<>());
        }
        for (Map.Entry<String, ReportDiscovery> entry : reports.entrySet()) {
            String owner = owningSelector(entry.getKey(), selectors);
            if (owner == null) {
                throw new IllegalStateException(
                        "discovered report class has no selector owner: " + entry.getKey());
            }
            bySelector.get(owner).add(entry);
        }

        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write("module\tsource_fqcn\treport_fqcn\tdiscovered_test_nodes\truntime_deferred_containers\tengine_ids\n");
            for (String selector : selectors) {
                List<Map.Entry<String, ReportDiscovery>> owned = bySelector.get(selector);
                owned.sort(Map.Entry.comparingByKey());
                if (owned.isEmpty()) {
                    writeRow(writer, module, selector, "none", "0", "0", "none");
                    continue;
                }
                for (Map.Entry<String, ReportDiscovery> entry : owned) {
                    ReportDiscovery report = entry.getValue();
                    writeRow(
                            writer,
                            module,
                            selector,
                            entry.getKey(),
                            Integer.toString(report.testNodes),
                            Integer.toString(report.runtimeDeferredContainers),
                            String.join(",", report.engines));
                }
            }
        }
    }

    private static List<String> readSelectors(Path path) throws Exception {
        Set<String> selectors = new TreeSet<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!"source_fqcn".equals(header)) {
                throw new IllegalArgumentException("unexpected selector header in " + path + ": " + header);
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (!value.isEmpty() && !selectors.add(value)) {
                    throw new IllegalArgumentException("duplicate selector in " + path + ": " + value);
                }
            }
        }
        return new ArrayList<>(selectors);
    }

    private static List<TestIdentifier> allIdentifiers(TestPlan plan) {
        List<TestIdentifier> identifiers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<TestIdentifier> queue = new ArrayList<>(plan.getRoots());
        for (int index = 0; index < queue.size(); index++) {
            TestIdentifier current = queue.get(index);
            if (!seen.add(current.getUniqueId())) {
                continue;
            }
            identifiers.add(current);
            List<TestIdentifier> children = new ArrayList<>(plan.getChildren(current));
            children.sort(Comparator.comparing(TestIdentifier::getUniqueId));
            queue.addAll(children);
        }
        return identifiers;
    }

    private static Optional<String> sourceClass(Optional<TestSource> source) {
        if (source.isEmpty()) {
            return Optional.empty();
        }
        TestSource value = source.get();
        if (value instanceof MethodSource methodSource) {
            return Optional.of(methodSource.getClassName());
        }
        if (value instanceof ClassSource classSource) {
            return Optional.of(classSource.getClassName());
        }
        return Optional.empty();
    }

    private static boolean isRuntimeDeferredContainer(TestIdentifier identifier) {
        return identifier.isContainer()
                && !identifier.isTest()
                && identifier.getSource().filter(MethodSource.class::isInstance).isPresent();
    }

    private static String owningSelector(String reportClass, List<String> selectors) {
        String owner = null;
        for (String selector : selectors) {
            if (reportClass.equals(selector) || reportClass.startsWith(selector + "$")) {
                if (owner != null && selector.length() <= owner.length()) {
                    continue;
                }
                owner = selector;
            }
        }
        return owner;
    }

    private static void writeRow(BufferedWriter writer, String... values) throws Exception {
        writer.write(String.join("\t", values));
        writer.write('\n');
    }

    private static final class ReportDiscovery {
        private int testNodes;
        private int runtimeDeferredContainers;
        private final Set<String> engines = new TreeSet<>();
    }
}
