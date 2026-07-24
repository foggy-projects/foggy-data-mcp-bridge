package com.foggyframework.dataset.model.api;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Repository guard for the 9.5.0 legacy coordinate, package and query-bypass exit. */
class LegacyExitArchitectureTest {

    private static final String LEGACY_ARTIFACT =
            "<artifactId>foggy-dataset-model</artifactId>";
    private static final String LEGACY_MODULE =
            "<module>foggy-dataset-model</module>";
    private static final String LEGACY_PACKAGE =
            "com.foggyframework.dataset.db.model";
    private static final String LEGACY_ADAPTER =
            "LegacyQueryFacadeAdapter";
    private static final Pattern DIRECT_MODEL_QUERY = Pattern.compile(
            "\\b(?:dataSetModel|queryModel|model)\\s*\\.query\\s*\\(");

    @Test
    void legacyAggregateCoordinateAndModuleCannotReappear() throws Exception {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (Path pom : files(root, "pom.xml")) {
            String source = Files.readString(pom, StandardCharsets.UTF_8);
            if (source.contains(LEGACY_ARTIFACT) || source.contains(LEGACY_MODULE)) {
                violations.add(relative(root, pom));
            }
        }
        assertTrue(violations.isEmpty(),
                "9.5.0 forbids the legacy aggregate coordinate/module: " + violations);
    }

    @Test
    void legacyPackageCannotReappearInProductionCodeOrRuntimeMetadata() throws Exception {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (Path file : productionAndRuntimeFiles(root)) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (source.contains(LEGACY_PACKAGE)) {
                violations.add(relative(root, file));
            }
        }
        assertTrue(violations.isEmpty(),
                "9.5.0 forbids the legacy Java package in production/runtime files: " + violations);
    }

    @Test
    void deprecatedAdapterAndHardAutoConfigurationImportsCannotReappear() throws Exception {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (Path javaFile : productionJavaFiles(root)) {
            String relative = relative(root, javaFile);
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            if (source.contains(LEGACY_ADAPTER)) {
                violations.add(relative + " uses " + LEGACY_ADAPTER);
            }
            if (!relative.startsWith("foggy-dataset-model-engine/")
                    && source.contains("import com.foggyframework.dataset.model.DbModelAutoConfiguration;")) {
                violations.add(relative + " hard-links DbModelAutoConfiguration");
            }
        }
        assertTrue(violations.isEmpty(), "legacy exit violations: " + violations);
    }

    @Test
    void governedEngineModelQueryBypassesAreZero() throws Exception {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (Path javaFile : productionJavaFiles(root)) {
            String relative = relative(root, javaFile);
            if (!isGovernedConsumer(relative)) {
                continue;
            }
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            boolean importsEngineQueryModel =
                    source.contains("import com.foggyframework.dataset.model.spi.QueryModel;")
                            || source.contains("import com.foggyframework.dataset.model.spi.QueryModelLoader;");
            if (importsEngineQueryModel && DIRECT_MODEL_QUERY.matcher(source).find()) {
                violations.add(relative);
            }
        }
        assertTrue(violations.isEmpty(),
                "controller/runtime/addon direct engine model.query bypasses require stable ports: "
                        + violations);
    }

    private static List<Path> productionJavaFiles(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains(
                            File.separator + "src" + File.separator + "main"
                                    + File.separator + "java" + File.separator))
                    .filter(LegacyExitArchitectureTest::isRepositorySource)
                    .toList();
        }
    }

    private static List<Path> productionAndRuntimeFiles(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(LegacyExitArchitectureTest::isRepositorySource)
                    .filter(path -> {
                        String value = path.toString();
                        boolean productionJava = value.contains(File.separator + "src" + File.separator + "main"
                                + File.separator + "java" + File.separator)
                                && value.endsWith(".java");
                        boolean runtimeText = value.contains(File.separator + "src" + File.separator + "main"
                                + File.separator + "resources" + File.separator)
                                && (value.endsWith(".imports")
                                || value.endsWith(".properties")
                                || value.endsWith(".xml")
                                || value.endsWith(".json")
                                || value.endsWith(".yml")
                                || value.endsWith(".yaml")
                                || value.endsWith("spring.factories"));
                        return productionJava || runtimeText;
                    })
                    .toList();
        }
    }

    private static List<Path> files(Path root, String fileName) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .filter(LegacyExitArchitectureTest::isRepositorySource)
                    .toList();
        }
    }

    private static boolean isRepositorySource(Path path) {
        String value = path.toString();
        return !value.contains(File.separator + "target" + File.separator)
                && !value.contains(File.separator + ".git" + File.separator);
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
        throw new IllegalStateException(
                "repository root not found from " + System.getProperty("user.dir"));
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    private static boolean isGovernedConsumer(String relative) {
        return relative.startsWith("addons/")
                || relative.startsWith("foggy-runtime-api/")
                || relative.startsWith("foggy-dataset-mcp/")
                || relative.startsWith("foggy-mcp-launcher/");
    }
}
