package com.foggyframework.dataset.mcp.experience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExperienceRecipeRegistryService storage/API contract")
class ExperienceRecipeRegistryServiceTest {
    private static final String DRAFT_KEY = "crm_source_funnel_and_stage_dropoff_dashboard@draft";
    private static final String PUBLISH_KEY = "sales_team_target_achievement_memory_grid_finance_owner@v1";

    @TempDir
    Path tempDir;

    private ExperienceRecipeRegistryService service;
    private JdbcExperienceRecipeRegistryStore store;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + tempDir.resolve("registry.db"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new JdbcExperienceRecipeRegistryStore(
                jdbcTemplate,
                new ExperienceRecipeRegistryProperties());
        store.initializeSchema();
        service = new ExperienceRecipeRegistryService(store, new DataSourceTransactionManager(dataSource));
    }

    @Test
    @DisplayName("create_draft_stub commits registry and event, replay creates no duplicate write")
    void shouldCreateDraftAndReplayIdempotently() {
        ExperienceRecipeRegistryMutationRequest create = draftRequest(DRAFT_KEY, "idem:create:draft");

        ExperienceRecipeRegistryResponse created = service.mutate(create);
        ExperienceRecipeRegistryResponse replay = service.mutate(create);

        assertEquals(ExperienceRecipeApiResult.CREATED, created.getApiResult());
        assertEquals(ExperienceRecipeStatus.DRAFT, created.getStatus());
        assertEquals(1L, created.getRecordVersion());
        assertFalse(created.isDiscoverable());
        assertEquals(ExperienceRecipeApiResult.IDEMPOTENT_REPLAY, replay.getApiResult());
        assertEquals(ExperienceRecipeStatus.DRAFT, replay.getStatus());
        assertEquals(1, count("experience_recipe_registry"));
        assertEquals(1, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("auto-init applies versioned JDBC schema resource idempotently")
    void shouldAutoInitVersionedJdbcSchemaIdempotently() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + tempDir.resolve("auto-init.db"));
        JdbcTemplate autoInitJdbcTemplate = new JdbcTemplate(dataSource);
        ExperienceRecipeRegistryProperties properties = new ExperienceRecipeRegistryProperties();
        properties.setAutoInitSchema(true);
        JdbcExperienceRecipeRegistryStore store = new JdbcExperienceRecipeRegistryStore(autoInitJdbcTemplate, properties);

        store.init();
        store.init();

        assertEquals(1, autoInitJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM experience_recipe_registry_schema_history WHERE version = ?",
                Integer.class,
                ExperienceRecipeRegistrySchemaInitializer.SCHEMA_VERSION));
        assertEquals(0, autoInitJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM experience_recipe_registry",
                Integer.class));
        assertEquals(0, autoInitJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM experience_recipe_registry_event",
                Integer.class));
        assertEquals(1, autoInitJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pragma_table_info('experience_recipe_registry') WHERE name = 'record_version'",
                Integer.class));
        assertEquals(1, autoInitJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pragma_table_info('experience_recipe_registry') WHERE name = 'namespace_scope'",
                Integer.class));
        assertEquals(1, autoInitJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pragma_table_info('experience_recipe_registry') WHERE name = 'tenant_scope'",
                Integer.class));
        assertEquals(1, autoInitJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pragma_table_info('experience_recipe_registry') WHERE name = 'permission_tags'",
                Integer.class));
    }

    @Test
    @DisplayName("schema init backfills record_version for pre-CAS registry tables")
    void shouldBackfillRecordVersionForExistingRegistryTable() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + tempDir.resolve("pre-cas.db"));
        JdbcTemplate legacyJdbcTemplate = new JdbcTemplate(dataSource);
        legacyJdbcTemplate.execute("""
                CREATE TABLE experience_recipe_registry (
                    registry_key VARCHAR(255) PRIMARY KEY,
                    recipe_id VARCHAR(255) NOT NULL,
                    recipe_version VARCHAR(64) NOT NULL,
                    canonical_recipe_id VARCHAR(255),
                    title VARCHAR(500),
                    business_type VARCHAR(255),
                    route VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    active_for_discovery INTEGER NOT NULL,
                    owner_role VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        legacyJdbcTemplate.update("""
                INSERT INTO experience_recipe_registry(
                    registry_key, recipe_id, recipe_version, status, active_for_discovery, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, DRAFT_KEY, "crm_source_funnel_and_stage_dropoff_dashboard", "draft", "draft", 0);
        JdbcExperienceRecipeRegistryStore legacyStore = new JdbcExperienceRecipeRegistryStore(
                legacyJdbcTemplate,
                new ExperienceRecipeRegistryProperties());

        legacyStore.initializeSchema();

        assertEquals(1L, legacyJdbcTemplate.queryForObject(
                "SELECT record_version FROM experience_recipe_registry WHERE registry_key = ?",
                Long.class,
                DRAFT_KEY));
    }

    @Test
    @DisplayName("duplicate idempotency event conflict replays existing registry event")
    void shouldReplayWhenConcurrentIdempotencyInsertWinsRace() {
        ExperienceRecipeRegistryService racingService =
                new ExperienceRecipeRegistryService(new DuplicateIdempotencyStore(), null);

        ExperienceRecipeRegistryResponse replay = racingService.mutate(draftRequest(
                DRAFT_KEY,
                "idem:race:create:draft"));

        assertEquals(ExperienceRecipeApiResult.IDEMPOTENT_REPLAY, replay.getApiResult());
        assertEquals(ExperienceRecipeStatus.DRAFT, replay.getStatus());
        assertFalse(replay.isDiscoverable());
        assertEquals(ExperienceRecipeFailureStage.IDEMPOTENCY_REPLAY.wireValue(), replay.getFailureStage());
    }

    @Test
    @DisplayName("promote_draft_to_candidate updates status but keeps recipe undiscoverable")
    void shouldPromoteDraftToCandidate() {
        service.mutate(draftRequest(DRAFT_KEY, "idem:create:draft"));

        ExperienceRecipeRegistryResponse promoted = service.mutate(promoteRequest(DRAFT_KEY, "idem:promote:draft"));

        assertEquals(ExperienceRecipeApiResult.UPDATED, promoted.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, promoted.getStatus());
        assertEquals(2L, promoted.getRecordVersion());
        assertFalse(promoted.isActiveForDiscovery());
        assertFalse(promoted.isDiscoverable());
        assertEquals("candidate", statusOf(DRAFT_KEY));
        assertEquals(2, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("committed mutations increment record_version")
    void shouldIncrementRecordVersionOnCommittedMutations() {
        ExperienceRecipeRegistryResponse created = service.mutate(draftRequest(
                PUBLISH_KEY,
                "idem:create:version-check"));
        ExperienceRecipeRegistryResponse promoted = service.mutate(promoteRequest(
                PUBLISH_KEY,
                "idem:promote:version-check"));
        ExperienceRecipeRegistryResponse published = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:version-check",
                "registry_admin",
                ExperienceRecipeGovernanceEvidence.passed()));

        assertEquals(1L, created.getRecordVersion());
        assertEquals(2L, promoted.getRecordVersion());
        assertEquals(3L, published.getRecordVersion());
        assertEquals(3L, recordVersionOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("stale expectedRecordVersion rejects mutation before registry write")
    void shouldRejectStaleExpectedRecordVersion() {
        service.mutate(draftRequest(DRAFT_KEY, "idem:create:stale-version"));
        ExperienceRecipeRegistryMutationRequest promote = promoteRequest(
                DRAFT_KEY,
                "idem:promote:stale-version");
        promote.setExpectedRecordVersion(0L);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.mutate(promote));

        assertTrue(error.getMessage().contains("Expected recordVersion 0 but was 1"));
        assertEquals("draft", statusOf(DRAFT_KEY));
        assertEquals(1L, recordVersionOf(DRAFT_KEY));
        assertEquals(1, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("JDBC store rejects stale record_version CAS writes")
    void shouldRejectStaleJdbcStoreWrite() {
        service.mutate(draftRequest(DRAFT_KEY, "idem:create:jdbc-cas"));
        ExperienceRecipeRegistryEntry stale = store.findByRegistryKey(DRAFT_KEY).orElseThrow().copy();
        service.mutate(promoteRequest(DRAFT_KEY, "idem:promote:jdbc-cas"));
        stale.setStatus(ExperienceRecipeStatus.REJECTED);

        boolean saved = store.saveWithVersionCheck(stale, stale.getRecordVersion());

        assertFalse(saved);
        assertEquals("candidate", statusOf(DRAFT_KEY));
        assertEquals(2L, recordVersionOf(DRAFT_KEY));
    }

    @Test
    @DisplayName("publish_validated makes a validated recipe discoverable and search only returns active validated rows")
    void shouldPublishValidatedAndSearchDiscoverable() {
        seedCandidate(PUBLISH_KEY);

        ExperienceRecipeRegistryMutationRequest publish = publishRequest(
                PUBLISH_KEY,
                "idem:publish:sales-team-target",
                "registry_admin",
                ExperienceRecipeGovernanceEvidence.passed());
        ExperienceRecipeRegistryResponse published = service.mutate(publish);
        ExperienceRecipeRegistryResponse search = service.searchDiscoverable(new ExperienceRecipeSearchRequest());

        assertEquals(ExperienceRecipeApiResult.UPDATED, published.getApiResult());
        assertEquals(ExperienceRecipeStatus.VALIDATED, published.getStatus());
        assertTrue(published.isActiveForDiscovery());
        assertTrue(published.isDiscoverable());
        assertEquals("validated", statusOf(PUBLISH_KEY));
        assertEquals(1, search.returnedRegistryKeys().size());
        assertEquals(PUBLISH_KEY, search.returnedRegistryKeys().get(0));
    }

    @Test
    @DisplayName("search governance filters by namespace tenant permission and owner")
    void shouldFilterDiscoverableRecipesByGovernanceMetadata() {
        seedPublished(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        seedPublished(DRAFT_KEY, "odoo", "tenant-b", "crm:read", "finance_owner");
        seedPublished("service_ticket_first_response_sla@v1", "odoo", "tenant-a", "support:read", "support_owner");

        ExperienceRecipeSearchRequest request = new ExperienceRecipeSearchRequest();
        request.setNamespace("odoo");
        request.setTenantId("tenant-a");
        request.setPermissionTags(Set.of("crm:read", "finance:read"));
        request.setOwnerRoles(Set.of("finance_owner"));

        ExperienceRecipeRegistryResponse response = service.searchDiscoverable(request);

        assertEquals(List.of(PUBLISH_KEY), response.returnedRegistryKeys());
        assertEquals("selected", response.getGovernanceDecision());
        assertEquals(1, response.getGovernanceFilteredCounts().get("tenant_mismatch"));
        assertEquals(1, response.getGovernanceFilteredCounts().get("permission_mismatch"));
    }

    @Test
    @DisplayName("search governance dedupes same canonical recipes before limit")
    void shouldDeduplicateCanonicalRecipesBeforeLimit() {
        seedPublished("crm_source_funnel_and_stage_dropoff_dashboard@v1", null, null, null, "data_analyst");
        seedPublished("crm_source_funnel_and_stage_dropoff_dashboard@v2", null, null, null, "data_analyst");

        ExperienceRecipeSearchRequest request = new ExperienceRecipeSearchRequest();
        request.setLimit(10);
        ExperienceRecipeRegistryResponse response = service.searchDiscoverable(request);

        assertEquals(List.of("crm_source_funnel_and_stage_dropoff_dashboard@v2"), response.returnedRegistryKeys());
        assertEquals(List.of("crm_source_funnel_and_stage_dropoff_dashboard"), response.getCandidateCanonicalGroups());
        assertFalse(response.isConflictExposed());
    }

    @Test
    @DisplayName("search governance exposes cross-canonical conflicts")
    void shouldExposeCrossCanonicalConflicts() {
        seedPublished(PUBLISH_KEY, null, null, null, "data_analyst");
        seedPublished("crm_target_month_conversion_window@v1", null, null, null, "data_analyst");

        ExperienceRecipeRegistryResponse response = service.searchDiscoverable(new ExperienceRecipeSearchRequest());

        assertEquals(2, response.returnedRegistryKeys().size());
        assertEquals("conflict_exposed", response.getGovernanceDecision());
        assertTrue(response.isConflictExposed());
        assertEquals(2, response.getCandidateCanonicalGroups().size());
    }

    @Test
    @DisplayName("publish_validated without governance evidence appends blocked event but does not mutate registry")
    void shouldBlockPublishWithoutGovernanceEvidence() {
        seedCandidate(DRAFT_KEY);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                DRAFT_KEY,
                "idem:publish:blocked",
                "data_analyst",
                new ExperienceRecipeGovernanceEvidence()));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertEquals(ExperienceRecipeFailureStage.GATE_VALIDATION.wireValue(), blocked.getFailureStage());
        assertFalse(blocked.isDiscoverable());
        assertEquals("candidate", statusOf(DRAFT_KEY));
        assertEquals(3, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("deprecate and reject remove recipes from discovery")
    void shouldDeprecateAndRejectWithoutDiscovery() {
        seedCandidate(PUBLISH_KEY);
        service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:sales-team-target",
                "registry_admin",
                ExperienceRecipeGovernanceEvidence.passed()));

        ExperienceRecipeRegistryMutationRequest deprecate = baseRequest(
                ExperienceRecipeRegistryOperation.DEPRECATE_RECIPE,
                PUBLISH_KEY,
                "idem:deprecate:sales-team-target",
                "registry_admin");
        deprecate.setExpectedFromStatus(ExperienceRecipeStatus.VALIDATED);
        deprecate.setExpectedFromActiveForDiscovery(true);
        ExperienceRecipeRegistryResponse deprecated = service.mutate(deprecate);

        seedCandidate(DRAFT_KEY);
        ExperienceRecipeRegistryMutationRequest reject = baseRequest(
                ExperienceRecipeRegistryOperation.REJECT_CANDIDATE,
                DRAFT_KEY,
                "idem:reject:crm-dashboard",
                "business_owner");
        reject.setExpectedFromStatus(ExperienceRecipeStatus.CANDIDATE);
        reject.setExpectedFromActiveForDiscovery(false);
        ExperienceRecipeRegistryResponse rejected = service.mutate(reject);

        assertEquals(ExperienceRecipeStatus.DEPRECATED, deprecated.getStatus());
        assertEquals(ExperienceRecipeStatus.REJECTED, rejected.getStatus());
        assertEquals("deprecated", statusOf(PUBLISH_KEY));
        assertEquals("rejected", statusOf(DRAFT_KEY));
        assertTrue(service.searchDiscoverable(new ExperienceRecipeSearchRequest()).returnedRegistryKeys().isEmpty());
    }

    @Test
    @DisplayName("registry and event write rollback together when event append fails")
    void shouldRollbackRegistryAndEventTogether() {
        seedCandidate(PUBLISH_KEY);

        ExperienceRecipeRegistryMutationRequest publish = publishRequest(
                PUBLISH_KEY,
                "idem:publish:rollback-drill",
                "registry_admin",
                ExperienceRecipeGovernanceEvidence.passed());
        publish.setSimulateFailureStage(ExperienceRecipeFailureStage.REGISTRY_EVENT_APPEND);
        ExperienceRecipeRegistryResponse rolledBack = service.mutate(publish);

        assertEquals(ExperienceRecipeApiResult.ROLLED_BACK, rolledBack.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, rolledBack.getStatus());
        assertEquals(ExperienceRecipeFailureStage.REGISTRY_EVENT_APPEND.wireValue(), rolledBack.getFailureStage());
        assertEquals("candidate", statusOf(PUBLISH_KEY));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM experience_recipe_registry_event WHERE idempotency_key = ?",
                Integer.class,
                "idem:publish:rollback-drill"));
    }

    private void seedCandidate(String registryKey) {
        service.mutate(draftRequest(registryKey, "idem:create:" + registryKey));
        service.mutate(promoteRequest(registryKey, "idem:promote:" + registryKey));
    }

    private void seedPublished(
            String registryKey,
            String namespaceScope,
            String tenantScope,
            String permissionTags,
            String ownerRole) {
        service.mutate(draftRequest(
                registryKey,
                "idem:create:" + registryKey,
                namespaceScope,
                tenantScope,
                permissionTags,
                ownerRole));
        service.mutate(promoteRequest(registryKey, "idem:promote:" + registryKey));
        service.mutate(publishRequest(
                registryKey,
                "idem:publish:" + registryKey,
                "registry_admin",
                ExperienceRecipeGovernanceEvidence.passed()));
    }

    private ExperienceRecipeRegistryMutationRequest draftRequest(String registryKey, String idempotencyKey) {
        return draftRequest(registryKey, idempotencyKey, null, null, null, null);
    }

    private ExperienceRecipeRegistryMutationRequest draftRequest(
            String registryKey,
            String idempotencyKey,
            String namespaceScope,
            String tenantScope,
            String permissionTags,
            String ownerRole) {
        ExperienceRecipeRegistryMutationRequest request = baseRequest(
                ExperienceRecipeRegistryOperation.CREATE_DRAFT_STUB,
                registryKey,
                idempotencyKey,
                "data_analyst");
        request.setRecipeId(registryKey.substring(0, registryKey.indexOf('@')));
        request.setRecipeVersion(registryKey.substring(registryKey.indexOf('@') + 1));
        request.setCanonicalRecipeId(request.getRecipeId().replace("_finance_owner", ""));
        request.setTitle("Experience recipe " + request.getRecipeId());
        request.setBusinessType("crm_single_model_funnel");
        request.setRoute("DSL_CTE");
        request.setNamespaceScope(namespaceScope);
        request.setTenantScope(tenantScope);
        request.setPermissionTags(permissionTags);
        request.setOwnerRole(ownerRole);
        request.setExpectedFromStatus(ExperienceRecipeStatus.NONE);
        request.setExpectedFromActiveForDiscovery(false);
        return request;
    }

    private ExperienceRecipeRegistryMutationRequest promoteRequest(String registryKey, String idempotencyKey) {
        ExperienceRecipeRegistryMutationRequest request = baseRequest(
                ExperienceRecipeRegistryOperation.PROMOTE_DRAFT_TO_CANDIDATE,
                registryKey,
                idempotencyKey,
                "data_analyst");
        request.setExpectedFromStatus(ExperienceRecipeStatus.DRAFT);
        request.setExpectedFromActiveForDiscovery(false);
        return request;
    }

    private ExperienceRecipeRegistryMutationRequest publishRequest(
            String registryKey,
            String idempotencyKey,
            String actorRole,
            ExperienceRecipeGovernanceEvidence evidence) {
        ExperienceRecipeRegistryMutationRequest request = baseRequest(
                ExperienceRecipeRegistryOperation.PUBLISH_VALIDATED,
                registryKey,
                idempotencyKey,
                actorRole);
        request.setExpectedFromStatus(ExperienceRecipeStatus.CANDIDATE);
        request.setExpectedFromActiveForDiscovery(false);
        request.setGovernanceEvidence(evidence);
        return request;
    }

    private static ExperienceRecipeRegistryMutationRequest baseRequest(
            ExperienceRecipeRegistryOperation operation,
            String registryKey,
            String idempotencyKey,
            String actorRole) {
        ExperienceRecipeRegistryMutationRequest request = new ExperienceRecipeRegistryMutationRequest();
        request.setOperation(operation);
        request.setRegistryKey(registryKey);
        request.setIdempotencyKey(idempotencyKey);
        request.setActorRole(actorRole);
        return request;
    }

    private String statusOf(String registryKey) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM experience_recipe_registry WHERE registry_key = ?",
                String.class,
                registryKey);
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private long recordVersionOf(String registryKey) {
        return jdbcTemplate.queryForObject(
                "SELECT record_version FROM experience_recipe_registry WHERE registry_key = ?",
                Long.class,
                registryKey);
    }

    private static final class DuplicateIdempotencyStore implements ExperienceRecipeRegistryStore {
        private final InMemoryExperienceRecipeRegistryStore delegate = new InMemoryExperienceRecipeRegistryStore();

        @Override
        public Optional<ExperienceRecipeRegistryEntry> findByRegistryKey(String registryKey) {
            return delegate.findByRegistryKey(registryKey);
        }

        @Override
        public Optional<ExperienceRecipeRegistryEvent> findEventByIdempotencyKey(String idempotencyKey) {
            return delegate.findEventByIdempotencyKey(idempotencyKey);
        }

        @Override
        public void save(ExperienceRecipeRegistryEntry entry) {
            delegate.save(entry);
        }

        @Override
        public void appendEvent(ExperienceRecipeRegistryEvent event) {
            delegate.appendEvent(event);
            throw new DuplicateKeyException("simulated idempotency race");
        }

        @Override
        public List<ExperienceRecipeRegistryEntry> findDiscoverable() {
            return delegate.findDiscoverable();
        }
    }
}
