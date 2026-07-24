package com.foggyframework.dataset.model.api;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Repository guard for the explicitly retained 9.4.1 legacy adoption surface. */
class LegacyAdoptionArchitectureTest {

    private static final String LEGACY_ARTIFACT = "foggy-dataset-model";
    private static final String LEGACY_IMPORT =
            "import com.foggyframework.dataset.db.model.";
    private static final Pattern DIRECT_MODEL_QUERY = Pattern.compile(
            "\\b(?:dataSetModel|queryModel|model)\\s*\\.query\\s*\\(");

    private static final Set<String> DIRECT_LEGACY_DEPENDENCY_ALLOWLIST = Set.of(
            "build-support/foggy-coverage-report/pom.xml",
            "foggy-dataset-memory-grid-bridge/pom.xml",
            "foggy-runtime-api/pom.xml",
            "foggy-dataset-memory-grid-duckdb/pom.xml",
            "foggy-dataset-mcp/pom.xml",
            "addons/foggy-dataset-pivot/pom.xml",
            "addons/foggy-dataset-client/pom.xml",
            "addons/foggy-dataset-model-cache/pom.xml",
            "addons/foggy-dataset-graphql/pom.xml",
            "addons/foggy-dataset-model-vector/pom.xml",
            "addons/foggy-dataset-model-mongo/pom.xml",
            "addons/foggy-dataset-model-preagg/pom.xml",
            "addons/foggy-benchmark-spider2/pom.xml",
            "addons/foggy-data-viewer/pom.xml"
    );

    private static final Map<String, Integer> LEGACY_IMPORT_CEILINGS = Map.ofEntries(
            Map.entry("addons/foggy-data-viewer", 15),
            Map.entry("addons/foggy-dataset-graphql", 3),
            Map.entry("addons/foggy-dataset-model-cache", 12),
            Map.entry("addons/foggy-dataset-model-mongo", 18),
            Map.entry("addons/foggy-dataset-model-preagg", 9),
            Map.entry("addons/foggy-dataset-model-vector", 6),
            Map.entry("foggy-dataset-mcp", 18),
            Map.entry("foggy-dataset-memory-grid-bridge", 14),
            Map.entry("foggy-dataset-memory-grid-duckdb", 1),
            Map.entry("foggy-mcp-launcher", 3),
            Map.entry("foggy-runtime-api", 11)
    );

    @Test
    void directLegacyAggregateDependenciesMatchTheDocumentedAllowlist() throws Exception {
        Path root = repositoryRoot();
        Set<String> actual = new LinkedHashSet<>();
        for (Path pom : files(root, "pom.xml")) {
            String relative = relative(root, pom);
            if (!relative.equals("pom.xml")
                    && !relative.equals("foggy-dataset-model/pom.xml")
                    && hasDirectDependency(pom, LEGACY_ARTIFACT)) {
                actual.add(relative);
            }
        }

        assertEquals(DIRECT_LEGACY_DEPENDENCY_ALLOWLIST, actual,
                "legacy aggregate dependency changes require an explicit 9.4.1 allowlist update");
    }

    @Test
    void productionLegacyImportsCannotExpandBeyondApprovedModulesAndCeilings() throws Exception {
        Path root = repositoryRoot();
        Map<String, Integer> actual = new LinkedHashMap<>();
        for (Path javaFile : productionJavaFiles(root)) {
            String relative = relative(root, javaFile);
            if (relative.startsWith("foggy-dataset-model/")) {
                continue;
            }
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            if (source.contains(LEGACY_IMPORT)) {
                actual.merge(module(relative), 1, Integer::sum);
            }
        }

        Set<String> unapprovedModules = new LinkedHashSet<>(actual.keySet());
        unapprovedModules.removeAll(LEGACY_IMPORT_CEILINGS.keySet());
        assertTrue(unapprovedModules.isEmpty(),
                "new modules import the legacy model package: " + unapprovedModules);
        actual.forEach((module, count) -> assertTrue(
                count <= LEGACY_IMPORT_CEILINGS.get(module),
                () -> module + " exceeds its legacy import ceiling: " + count));
    }

    @Test
    void deprecatedQueryAdaptersAndHardAutoConfigurationReferencesDoNotReappear() throws Exception {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (Path javaFile : productionJavaFiles(root)) {
            String relative = relative(root, javaFile);
            if (relative.startsWith("foggy-dataset-model/")) {
                continue;
            }
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            if (source.contains("import com.foggyframework.dataset.db.model.service.LegacyQueryFacadeAdapter;")) {
                violations.add(relative + " uses LegacyQueryFacadeAdapter");
            }
            if (source.contains("import com.foggyframework.dataset.db.model.DbModelAutoConfiguration;")) {
                violations.add(relative + " hard-links DbModelAutoConfiguration");
            }
        }
        assertTrue(violations.isEmpty(), "legacy adoption violations: " + violations);
    }

    @Test
    void governedDirectModelQueryBypassesMatchTheExplicitAllowlist() throws Exception {
        Path root = repositoryRoot();
        Set<String> actual = new LinkedHashSet<>();
        for (Path javaFile : productionJavaFiles(root)) {
            String relative = relative(root, javaFile);
            if (!isGovernedConsumer(relative)) {
                continue;
            }
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            if (DIRECT_MODEL_QUERY.matcher(source).find()) {
                actual.add(relative);
            }
        }

        assertEquals(Set.of(
                "addons/foggy-dataset-client/src/main/java/"
                        + "com/foggyframework/dataset/client/proxy/DatasetClientProxy.java"
        ), actual, "new controller/runtime/addon model.query bypass requires explicit approval");
    }

    private static boolean hasDirectDependency(Path pom, String artifactId) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Element project = factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
        for (Element dependencies : directChildren(project, "dependencies")) {
            for (Element dependency : directChildren(dependencies, "dependency")) {
                for (Element artifact : directChildren(dependency, "artifactId")) {
                    if (artifactId.equals(artifact.getTextContent().trim())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static List<Path> productionJavaFiles(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains(
                            File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator))
                    .filter(path -> !path.toString().contains(File.separator + "target" + File.separator))
                    .toList();
        }
    }

    private static List<Path> files(Path root, String fileName) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .filter(path -> !path.toString().contains(File.separator + "target" + File.separator))
                    .filter(path -> !path.toString().contains(File.separator + ".git" + File.separator))
                    .toList();
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("foggy-dataset-model-api"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found from " + System.getProperty("user.dir"));
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    private static String module(String relative) {
        String[] parts = relative.split("/");
        return "addons".equals(parts[0]) ? parts[0] + "/" + parts[1] : parts[0];
    }

    private static boolean isGovernedConsumer(String relative) {
        return relative.startsWith("addons/")
                || relative.startsWith("foggy-runtime-api/")
                || relative.startsWith("foggy-dataset-mcp/")
                || relative.startsWith("foggy-mcp-launcher/");
    }
}
