package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.RuntimeDatasourceRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeDatasourceRegistryGenerationTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void v1RegistryMigratesAtomicallyAndRestartKeepsPersistedGenerations() throws Exception {
        Path registryPath = tempDir.resolve("runtime-datasources.json");
        Map<String, Object> v1 = new LinkedHashMap<>();
        v1.put("version", 1);
        v1.put("datasources", List.of(
                recordMap("zeta", "jdbc:h2:mem:zeta"),
                recordMap("alpha", "jdbc:h2:mem:alpha")
        ));
        v1.put("namespaceBindings", Map.of("tenant-z", "zeta", "tenant-a", "alpha"));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), v1);

        RuntimeDatasourceRegistryService first = service(registryPath);
        Map<String, String> firstGenerations = generations(first.listRecords());
        String firstTenantGeneration = first.getNamespaceBindingGeneration("tenant-a").orElseThrow();
        JsonNode migrated = objectMapper.readTree(registryPath.toFile());

        assertThat(migrated.path("version").asInt()).isEqualTo(2);
        assertThat(migrated.path("registryEpoch").asText()).isNotBlank();
        assertThat(migrated.path("nextSequence").asLong()).isEqualTo(5L);
        assertThat(firstGenerations).containsOnlyKeys("alpha", "zeta");
        assertThat(firstGenerations.values()).doesNotHaveDuplicates();
        assertThat(firstGenerations.get("alpha")).endsWith(":1");
        assertThat(firstGenerations.get("zeta")).endsWith(":2");
        assertThat(firstTenantGeneration).endsWith(":3");
        assertThat(Files.exists(registryPath.resolveSibling(registryPath.getFileName() + ".tmp"))).isFalse();

        RuntimeDatasourceRegistryService restarted = service(registryPath);

        assertThat(generations(restarted.listRecords())).isEqualTo(firstGenerations);
        assertThat(restarted.getNamespaceBindingGeneration("tenant-a"))
                .contains(firstTenantGeneration);
        assertThat(restarted.registryEpoch()).isEqualTo(first.registryEpoch());
        assertThat(restarted.nextSequence()).isEqualTo(first.nextSequence());
    }

    @Test
    void saveDisableRemoveAndRecreateNeverReuseGeneration() {
        Path registryPath = tempDir.resolve("mutations.json");
        RuntimeDatasourceRegistryService registry = service(registryPath);
        RuntimeDatasourceRecord first = registry.save(registry.newRecord(
                "sales", "h2", "jdbc:h2:mem:first", "sa", null, null, true));
        RuntimeDatasourceRecord equivalentSave = registry.save(registry.newRecord(
                "sales", "h2", "jdbc:h2:mem:first", "sa", null, null, true));
        RuntimeDatasourceRecord disabled = registry.save(registry.newRecord(
                "sales", "h2", "jdbc:h2:mem:first", "sa", null, null, false));

        assertThat(List.of(
                first.bindingGeneration(),
                equivalentSave.bindingGeneration(),
                disabled.bindingGeneration()
        )).doesNotHaveDuplicates();
        assertThat(registry.remove("sales")).isTrue();
        RuntimeDatasourceRecord recreated = registry.save(registry.newRecord(
                "sales", "h2", "jdbc:h2:mem:recreated", "sa", null, null, true));

        assertThat(recreated.bindingGeneration())
                .isNotIn(first.bindingGeneration(), equivalentSave.bindingGeneration(), disabled.bindingGeneration());
        RuntimeDatasourceRegistryService restarted = service(registryPath);
        assertThat(restarted.find("sales").orElseThrow().bindingGeneration())
                .isEqualTo(recreated.bindingGeneration());
        assertThat(restarted.nextSequence()).isGreaterThan(4L);
    }

    @Test
    void namespaceRebindAndDatasourceSaveAdvanceNamespaceGeneration() {
        Path registryPath = tempDir.resolve("namespace.json");
        RuntimeDatasourceRegistryService registry = service(registryPath);
        registry.save(registry.newRecord(
                "sales-a", "h2", "jdbc:h2:mem:a", "sa", null, null, true));
        registry.save(registry.newRecord(
                "sales-b", "h2", "jdbc:h2:mem:b", "sa", null, null, true));
        registry.bindNamespace("tenant", "sales-a");
        String first = registry.getNamespaceBindingGeneration("tenant").orElseThrow();

        registry.bindNamespace("tenant", "sales-b");
        String rebound = registry.getNamespaceBindingGeneration("tenant").orElseThrow();
        registry.save(registry.newRecord(
                "sales-b", "h2", "jdbc:h2:mem:b2", "sa", null, null, true));
        String backendChanged = registry.getNamespaceBindingGeneration("tenant").orElseThrow();

        assertThat(List.of(first, rebound, backendChanged)).doesNotHaveDuplicates();
        RuntimeDatasourceRegistryService restarted = service(registryPath);
        assertThat(restarted.getNamespaceDatasource("tenant")).contains("sales-b");
        assertThat(restarted.getNamespaceBindingGeneration("tenant")).contains(backendChanged);
    }

    @Test
    void serviceCanonicalizesDatasourceAndNamespaceKeysBeforePersistingIdentity() {
        Path registryPath = tempDir.resolve("canonical-keys.json");
        RuntimeDatasourceRegistryService registry = service(registryPath);
        RuntimeDatasourceRecord saved = registry.save(new RuntimeDatasourceRecord(
                " sales ",
                "h2",
                "jdbc:h2:mem:canonical",
                "sa",
                null,
                null,
                true,
                "2026-07-14T00:00:00Z",
                "2026-07-14T00:00:00Z"
        ));

        registry.bindNamespace(" tenant-a ", " sales ");

        String namespaceGeneration = registry
                .getNamespaceBindingGeneration(" tenant-a ")
                .orElseThrow();
        assertThat(saved.name()).isEqualTo("sales");
        assertThat(registry.find(" sales ")).contains(saved);
        assertThat(registry.listNamespaceBindings())
                .containsExactly(Map.entry("tenant-a", "sales"));
        assertThat(registry.currentness(new DatasourceBindingIdentity(
                "runtime:named:sales",
                "runtime-registry:sales",
                new DatasourceBindingGeneration(saved.bindingGeneration())
        ))).isEqualTo(BindingCurrentness.CURRENT);
        assertThat(registry.currentness(new DatasourceBindingIdentity(
                "runtime:namespace-default:tenant-a",
                "runtime-registry:sales",
                new DatasourceBindingGeneration(namespaceGeneration)
        ))).isEqualTo(BindingCurrentness.CURRENT);

        RuntimeDatasourceRegistryService restarted = service(registryPath);
        assertThat(restarted.find(" sales ")).isPresent();
        assertThat(restarted.getNamespaceDatasource(" tenant-a ")).contains("sales");
        assertThat(restarted.listNamespaceBindings())
                .containsExactly(Map.entry("tenant-a", "sales"));
    }

    @Test
    void existingV2RegistryCanonicalizesKeysAtomicallyOnLoad() throws Exception {
        Path registryPath = tempDir.resolve("canonicalize-existing-v2.json");
        String epoch = "fixed-canonical-epoch";
        Map<String, Object> record = recordMap(" sales ", "jdbc:h2:mem:canonical-v2");
        record.put("bindingGeneration", epoch + ":1");
        Map<String, Object> v2 = new LinkedHashMap<>();
        v2.put("version", 2);
        v2.put("registryEpoch", epoch);
        v2.put("nextSequence", 3);
        v2.put("datasources", List.of(record));
        v2.put("namespaceBindings", Map.of(" tenant-a ", " sales "));
        v2.put("namespaceBindingGenerations", Map.of(" tenant-a ", epoch + ":2"));
        objectMapper.writeValue(registryPath.toFile(), v2);

        RuntimeDatasourceRegistryService registry = service(registryPath);

        assertThat(registry.find(" sales ")).isPresent();
        assertThat(registry.listNamespaceBindings())
                .containsExactly(Map.entry("tenant-a", "sales"));
        assertThat(registry.getNamespaceBindingGeneration(" tenant-a "))
                .contains(epoch + ":2");
        JsonNode rewritten = objectMapper.readTree(registryPath.toFile());
        assertThat(rewritten.path("datasources").get(0).path("name").asText())
                .isEqualTo("sales");
        assertThat(rewritten.path("namespaceBindings").has("tenant-a")).isTrue();
        assertThat(rewritten.path("namespaceBindings").has(" tenant-a ")).isFalse();
        assertThat(rewritten.path("namespaceBindingGenerations").has("tenant-a"))
                .isTrue();
        assertThat(Files.exists(registryPath.resolveSibling(
                registryPath.getFileName() + ".tmp"))).isFalse();
    }

    @Test
    void corruptV2CounterRollbackFailsClosedInsteadOfReusingGeneration() throws Exception {
        Path registryPath = tempDir.resolve("corrupt.json");
        String epoch = "fixed-epoch";
        Map<String, Object> record = recordMap("sales", "jdbc:h2:mem:sales");
        record.put("bindingGeneration", epoch + ":1");
        Map<String, Object> v2 = new LinkedHashMap<>();
        v2.put("version", 2);
        v2.put("registryEpoch", epoch);
        v2.put("nextSequence", 1);
        v2.put("datasources", List.of(record));
        v2.put("namespaceBindings", Map.of());
        v2.put("namespaceBindingGenerations", Map.of());
        objectMapper.writeValue(registryPath.toFile(), v2);
        RuntimeDatasourceRegistryService registry = service(registryPath);

        assertThatThrownBy(registry::listRecords)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nextSequence");
        assertThatThrownBy(registry::listRecords)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nextSequence");
    }

    @Test
    void corruptV2MissingGenerationFailsClosedInsteadOfBeingMigrated() throws Exception {
        Path registryPath = tempDir.resolve("missing-generation.json");
        Map<String, Object> v2 = new LinkedHashMap<>();
        v2.put("version", 2);
        v2.put("registryEpoch", "fixed-epoch");
        v2.put("nextSequence", 2);
        v2.put("datasources", List.of(recordMap("sales", "jdbc:h2:mem:sales")));
        v2.put("namespaceBindings", Map.of());
        v2.put("namespaceBindingGenerations", Map.of());
        objectMapper.writeValue(registryPath.toFile(), v2);
        byte[] original = Files.readAllBytes(registryPath);
        RuntimeDatasourceRegistryService registry = service(registryPath);

        assertThatThrownBy(registry::listRecords)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing or duplicate binding generation");
        assertThat(Files.readAllBytes(registryPath)).isEqualTo(original);
    }

    private RuntimeDatasourceRegistryService service(Path path) {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getDatasourceRegistry().setPath(path.toString());
        ManagedDataSourcePoolManager manager = new ManagedDataSourcePoolManager(
                properties,
                (record, password, settings) -> {
                    throw new AssertionError("registry generation tests must not create a physical pool");
                },
                Clock.systemUTC(),
                null,
                false
        );
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        return new RuntimeDatasourceRegistryService(
                properties,
                beanFactory.getBeanProvider(DataSource.class),
                objectMapper,
                manager
        );
    }

    private static Map<String, Object> recordMap(String name, String jdbcUrl) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", name);
        record.put("type", "h2");
        record.put("jdbcUrl", jdbcUrl);
        record.put("username", "sa");
        record.put("enabled", true);
        record.put("createdAt", "2026-07-13T00:00:00Z");
        record.put("updatedAt", "2026-07-13T00:00:00Z");
        return record;
    }

    private static Map<String, String> generations(List<RuntimeDatasourceRecord> records) {
        Map<String, String> generations = new LinkedHashMap<>();
        records.forEach(record -> generations.put(record.name(), record.bindingGeneration()));
        return generations;
    }
}
