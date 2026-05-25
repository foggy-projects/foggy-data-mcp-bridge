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
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExperienceRecipeRegistryService storage/API contract")
class ExperienceRecipeRegistryServiceTest {
    private static final String DRAFT_KEY = "crm_source_funnel_and_stage_dropoff_dashboard@draft";
    private static final String PUBLISH_KEY = "sales_team_target_achievement_memory_grid_finance_owner@v1";
    private static final Instant SIGNED_AT = Instant.parse("2026-05-20T10:15:30Z");
    private static final Clock SIGNING_CLOCK = Clock.fixed(SIGNED_AT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private ExperienceRecipeRegistryService service;
    private JdbcExperienceRecipeRegistryStore store;
    private JdbcTemplate jdbcTemplate;
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:sqlite:" + tempDir.resolve("registry.db"));
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
        assertEquals(1, autoInitJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pragma_table_info('experience_recipe_registry_event') "
                        + "WHERE name = 'evidence_artifacts_json'",
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
        assertEquals(5, published.getEvidenceArtifacts().size());
        assertEquals("validated", statusOf(PUBLISH_KEY));
        assertEquals(1, search.returnedRegistryKeys().size());
        assertEquals(PUBLISH_KEY, search.returnedRegistryKeys().get(0));
        String eventArtifactsJson = jdbcTemplate.queryForObject(
                "SELECT evidence_artifacts_json FROM experience_recipe_registry_event WHERE idempotency_key = ?",
                String.class,
                "idem:publish:sales-team-target");
        assertNotNull(eventArtifactsJson);
        assertTrue(eventArtifactsJson.contains("owner_signoff"));
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
    @DisplayName("publish_validated blocks passed statuses without required evidence artifacts")
    void shouldBlockPublishWithoutEvidenceArtifacts() {
        seedCandidate(DRAFT_KEY);
        ExperienceRecipeGovernanceEvidence evidence = ExperienceRecipeGovernanceEvidence.passed();
        evidence.setEvidenceArtifacts(List.of());

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                DRAFT_KEY,
                "idem:publish:blocked-artifacts",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertEquals(ExperienceRecipeFailureStage.GATE_VALIDATION.wireValue(), blocked.getFailureStage());
        assertTrue(blocked.getEvidenceArtifacts().isEmpty());
        assertEquals("candidate", statusOf(DRAFT_KEY));
        assertEquals(3, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("publish_validated blocks evidence artifacts with malformed sha256 hash")
    void shouldBlockPublishWithMalformedArtifactHash() {
        seedCandidate(DRAFT_KEY);
        ExperienceRecipeGovernanceEvidence evidence = ExperienceRecipeGovernanceEvidence.passed();
        List<ExperienceRecipeEvidenceArtifact> artifacts = evidence.getEvidenceArtifacts();
        artifacts.get(0).setArtifactHash("sha256:owner-signoff");
        evidence.setEvidenceArtifacts(artifacts);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                DRAFT_KEY,
                "idem:publish:blocked-artifact-hash",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertEquals(ExperienceRecipeFailureStage.GATE_VALIDATION.wireValue(), blocked.getFailureStage());
        assertEquals(5, blocked.getEvidenceArtifacts().size());
        assertEquals("candidate", statusOf(DRAFT_KEY));
        assertEquals(3, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("publish_validated blocks evidence artifacts with unsupported URI scheme")
    void shouldBlockPublishWithUnsupportedArtifactUri() {
        seedCandidate(DRAFT_KEY);
        ExperienceRecipeGovernanceEvidence evidence = ExperienceRecipeGovernanceEvidence.passed();
        List<ExperienceRecipeEvidenceArtifact> artifacts = evidence.getEvidenceArtifacts();
        artifacts.get(0).setArtifactUri("javascript:alert(1)");
        evidence.setEvidenceArtifacts(artifacts);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                DRAFT_KEY,
                "idem:publish:blocked-artifact-uri",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertEquals(ExperienceRecipeFailureStage.GATE_VALIDATION.wireValue(), blocked.getFailureStage());
        assertEquals(5, blocked.getEvidenceArtifacts().size());
        assertEquals("candidate", statusOf(DRAFT_KEY));
        assertEquals(3, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("publish_validated verifies resolved foggy artifact sha256 when artifact resolution is required")
    void shouldPublishValidatedWithResolvedArtifactHashes() throws IOException {
        Path artifactRoot = tempDir.resolve("artifacts");
        enableArtifactResolution(artifactRoot);
        seedCandidate(PUBLISH_KEY);
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(artifactRoot, null);

        ExperienceRecipeRegistryResponse published = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:resolved-artifacts",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.UPDATED, published.getApiResult());
        assertEquals(ExperienceRecipeStatus.VALIDATED, published.getStatus());
        assertTrue(published.isDiscoverable());
        assertEquals("validated", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated blocks missing artifact content when artifact resolution is required")
    void shouldBlockPublishWhenRequiredArtifactCannotBeResolved() throws IOException {
        Path artifactRoot = tempDir.resolve("artifacts");
        enableArtifactResolution(artifactRoot);
        seedCandidate(DRAFT_KEY);
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(
                artifactRoot,
                ExperienceRecipeGovernanceEvidence.OWNER_SIGNOFF_ARTIFACT);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                DRAFT_KEY,
                "idem:publish:missing-artifact",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertEquals(ExperienceRecipeFailureStage.GATE_VALIDATION.wireValue(), blocked.getFailureStage());
        assertTrue(blocked.getMessage().contains("cannot be resolved"));
        assertEquals("candidate", statusOf(DRAFT_KEY));
        assertEquals(3, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("publish_validated blocks artifact content with sha256 mismatch")
    void shouldBlockPublishWhenResolvedArtifactHashMismatches() throws IOException {
        Path artifactRoot = tempDir.resolve("artifacts");
        enableArtifactResolution(artifactRoot);
        seedCandidate(DRAFT_KEY);
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(artifactRoot, null);
        List<ExperienceRecipeEvidenceArtifact> artifacts = evidence.getEvidenceArtifacts();
        artifacts.get(0).setArtifactHash("sha256:" + "f".repeat(64));
        evidence.setEvidenceArtifacts(artifacts);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                DRAFT_KEY,
                "idem:publish:artifact-hash-mismatch",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertEquals(ExperienceRecipeFailureStage.GATE_VALIDATION.wireValue(), blocked.getFailureStage());
        assertTrue(blocked.getMessage().contains("hash mismatched"));
        assertEquals("candidate", statusOf(DRAFT_KEY));
        assertEquals(3, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("publish_validated blocks foggy artifact URI escaping artifact root")
    void shouldBlockPublishWhenFoggyArtifactEscapesRoot() throws IOException {
        Path artifactRoot = tempDir.resolve("artifacts");
        enableArtifactResolution(artifactRoot);
        seedCandidate(DRAFT_KEY);
        String outsideContent = "outside-owner-signoff";
        Files.writeString(tempDir.resolve("outside-owner-signoff"), outsideContent, StandardCharsets.UTF_8);
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(artifactRoot, null);
        List<ExperienceRecipeEvidenceArtifact> artifacts = evidence.getEvidenceArtifacts();
        artifacts.get(0).setArtifactUri("foggy://experience-recipes/../../outside-owner-signoff");
        artifacts.get(0).setArtifactHash(ExperienceRecipeArtifactHash.sha256(outsideContent));
        evidence.setEvidenceArtifacts(artifacts);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                DRAFT_KEY,
                "idem:publish:artifact-path-escape",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertEquals(ExperienceRecipeFailureStage.GATE_VALIDATION.wireValue(), blocked.getFailureStage());
        assertTrue(blocked.getMessage().contains("cannot be resolved"));
        assertEquals("candidate", statusOf(DRAFT_KEY));
        assertEquals(3, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("publish_validated resolves HTTPS artifacts through composite resolvers")
    void shouldPublishValidatedWithCompositeRemoteArtifactResolver() {
        ExperienceRecipeRegistryProperties properties = new ExperienceRecipeRegistryProperties();
        properties.setRequireArtifactResolution(true);
        ExperienceRecipeArtifactResolver emptyResolver = artifact -> Optional.empty();
        ExperienceRecipeArtifactResolver remoteResolver =
                artifact -> Optional.of(remoteArtifactContent(artifact.getArtifactType()));
        service = new ExperienceRecipeRegistryService(
                store,
                new DataSourceTransactionManager(dataSource),
                List.of(emptyResolver, remoteResolver),
                null,
                properties);
        seedCandidate(PUBLISH_KEY);
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByRemoteArtifacts();

        ExperienceRecipeRegistryResponse published = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:remote-artifacts",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.UPDATED, published.getApiResult());
        assertEquals(ExperienceRecipeStatus.VALIDATED, published.getStatus());
        assertTrue(published.isDiscoverable());
        assertEquals("validated", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated allows remote artifacts when URI policy binds tenant owner and recipe")
    void shouldPublishValidatedWhenRemoteArtifactUriMatchesPolicy() {
        AtomicInteger resolverCalls = new AtomicInteger();
        ExperienceRecipeRegistryProperties properties = remotePolicyProperties();
        ExperienceRecipeArtifactResolver remoteResolver = artifact -> {
            resolverCalls.incrementAndGet();
            return Optional.of(remoteArtifactContent(artifact.getArtifactType()));
        };
        service = new ExperienceRecipeRegistryService(
                store,
                new DataSourceTransactionManager(dataSource),
                List.of(remoteResolver),
                null,
                properties);
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByScopedRemoteArtifacts(
                "tenant-a",
                "finance_owner",
                PUBLISH_KEY);

        ExperienceRecipeRegistryResponse published = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:remote-artifacts-policy-passed",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.UPDATED, published.getApiResult());
        assertEquals(ExperienceRecipeStatus.VALIDATED, published.getStatus());
        assertEquals(requiredArtifactTypes().size(), resolverCalls.get());
    }

    @Test
    @DisplayName("publish_validated blocks remote artifacts outside URI policy before resolver is called")
    void shouldBlockRemoteArtifactUriOutsidePolicyBeforeResolver() {
        AtomicInteger resolverCalls = new AtomicInteger();
        ExperienceRecipeRegistryProperties properties = remotePolicyProperties();
        ExperienceRecipeArtifactResolver remoteResolver = artifact -> {
            resolverCalls.incrementAndGet();
            return Optional.of(remoteArtifactContent(artifact.getArtifactType()));
        };
        service = new ExperienceRecipeRegistryService(
                store,
                new DataSourceTransactionManager(dataSource),
                List.of(remoteResolver),
                null,
                properties);
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByScopedRemoteArtifacts(
                "tenant-b",
                "finance_owner",
                PUBLISH_KEY);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:remote-artifacts-policy-blocked",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertTrue(blocked.getMessage().contains("artifact URI is not bound to recipe context"));
        assertEquals(0, resolverCalls.get());
        assertEquals("candidate", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated allows artifact object metadata bound to recipe context")
    void shouldPublishValidatedWhenArtifactObjectMetadataMatchesContext() {
        ExperienceRecipeRegistryProperties properties = objectMetadataPolicyProperties();
        service = new ExperienceRecipeRegistryService(
                store,
                new DataSourceTransactionManager(dataSource),
                List.of(),
                null,
                properties);
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByScopedRemoteArtifacts(
                "tenant-a",
                "finance_owner",
                PUBLISH_KEY);
        addObjectMetadata(evidence, "odoo", "tenant-a", "finance_owner", PUBLISH_KEY);

        ExperienceRecipeRegistryResponse published = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:artifact-object-metadata-passed",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.UPDATED, published.getApiResult());
        assertEquals(ExperienceRecipeStatus.VALIDATED, published.getStatus());
        assertTrue(published.isDiscoverable());
    }

    @Test
    @DisplayName("publish_validated blocks artifact object metadata when object identity is missing")
    void shouldBlockArtifactObjectMetadataWhenIdentityIsMissing() {
        ExperienceRecipeRegistryProperties properties = objectMetadataPolicyProperties();
        service = new ExperienceRecipeRegistryService(
                store,
                new DataSourceTransactionManager(dataSource),
                List.of(),
                null,
                properties);
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByScopedRemoteArtifacts(
                "tenant-a",
                "finance_owner",
                PUBLISH_KEY);
        addObjectMetadata(evidence, "odoo", "tenant-a", "finance_owner", PUBLISH_KEY);
        List<ExperienceRecipeEvidenceArtifact> artifacts = evidence.getEvidenceArtifacts();
        artifacts.forEach(artifact -> {
            artifact.setObjectVersion(null);
            artifact.setObjectEtag(null);
        });
        evidence.setEvidenceArtifacts(artifacts);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:artifact-object-identity-missing",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertTrue(blocked.getMessage().contains("object identity is missing"));
        assertEquals("candidate", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated blocks artifact object metadata mismatched with recipe context")
    void shouldBlockArtifactObjectMetadataMismatchBeforeResolver() {
        AtomicInteger resolverCalls = new AtomicInteger();
        ExperienceRecipeRegistryProperties properties = objectMetadataPolicyProperties();
        properties.setRequireArtifactResolution(true);
        ExperienceRecipeArtifactResolver remoteResolver = artifact -> {
            resolverCalls.incrementAndGet();
            return Optional.of(remoteArtifactContent(artifact.getArtifactType()));
        };
        service = new ExperienceRecipeRegistryService(
                store,
                new DataSourceTransactionManager(dataSource),
                List.of(remoteResolver),
                null,
                properties);
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByScopedRemoteArtifacts(
                "tenant-a",
                "finance_owner",
                PUBLISH_KEY);
        addObjectMetadata(evidence, "odoo", "tenant-b", "finance_owner", PUBLISH_KEY);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:artifact-object-metadata-blocked",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertTrue(blocked.getMessage().contains("object metadata is not bound to recipe context"));
        assertEquals(0, resolverCalls.get());
        assertEquals("candidate", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated allows trusted resolver object metadata bound to recipe context")
    void shouldPublishValidatedWhenTrustedResolverObjectMetadataMatchesContext() {
        ExperienceRecipeRegistryProperties properties = trustedObjectMetadataPolicyProperties();
        service = new ExperienceRecipeRegistryService(
                store,
                new DataSourceTransactionManager(dataSource),
                List.of(trustedObjectMetadataResolver("odoo", "tenant-a", "finance_owner", PUBLISH_KEY)),
                null,
                properties);
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByScopedRemoteArtifacts(
                "tenant-a",
                "finance_owner",
                PUBLISH_KEY);

        ExperienceRecipeRegistryResponse published = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:trusted-object-metadata-passed",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.UPDATED, published.getApiResult());
        assertEquals(ExperienceRecipeStatus.VALIDATED, published.getStatus());
        assertTrue(published.isDiscoverable());
    }

    @Test
    @DisplayName("publish_validated requires resolver object metadata in trusted metadata mode")
    void shouldBlockTrustedObjectMetadataWhenResolverReturnsContentOnly() {
        ExperienceRecipeRegistryProperties properties = trustedObjectMetadataPolicyProperties();
        ExperienceRecipeArtifactResolver contentOnlyResolver =
                artifact -> Optional.of(remoteArtifactContent(artifact.getArtifactType()));
        service = new ExperienceRecipeRegistryService(
                store,
                new DataSourceTransactionManager(dataSource),
                List.of(contentOnlyResolver),
                null,
                properties);
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByScopedRemoteArtifacts(
                "tenant-a",
                "finance_owner",
                PUBLISH_KEY);
        addObjectMetadata(evidence, "odoo", "tenant-a", "finance_owner", PUBLISH_KEY);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:trusted-object-metadata-content-only",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertTrue(blocked.getMessage().contains("object identity is missing"));
        assertEquals("candidate", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated blocks trusted resolver object metadata mismatched with recipe context")
    void shouldBlockTrustedResolverObjectMetadataMismatch() {
        AtomicInteger resolverCalls = new AtomicInteger();
        ExperienceRecipeRegistryProperties properties = trustedObjectMetadataPolicyProperties();
        ExperienceRecipeArtifactResolver resolver = trustedObjectMetadataResolver(
                "odoo",
                "tenant-b",
                "finance_owner",
                PUBLISH_KEY,
                resolverCalls);
        service = new ExperienceRecipeRegistryService(
                store,
                new DataSourceTransactionManager(dataSource),
                List.of(resolver),
                null,
                properties);
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByScopedRemoteArtifacts(
                "tenant-a",
                "finance_owner",
                PUBLISH_KEY);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:trusted-object-metadata-mismatch",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertTrue(blocked.getMessage().contains("object metadata is not bound to recipe context"));
        assertEquals(1, resolverCalls.get());
        assertEquals("candidate", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated verifies Ed25519 artifact signature when signature verification is required")
    void shouldPublishValidatedWithVerifiedArtifactSignatures() throws Exception {
        Path artifactRoot = tempDir.resolve("artifacts");
        TestSigningMaterial signing = signingMaterial("recipe-evidence-key", "tenant-a", "finance_owner");
        enableArtifactSignatureVerification(artifactRoot, signing.verifier());
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(artifactRoot, null);
        signArtifacts(evidence, signing, signatureContext(PUBLISH_KEY, "odoo", "tenant-a", "finance_owner"));

        ExperienceRecipeRegistryResponse published = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:verified-artifact-signatures",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.UPDATED, published.getApiResult());
        assertEquals(ExperienceRecipeStatus.VALIDATED, published.getStatus());
        assertTrue(published.isDiscoverable());
        assertEquals("validated", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated blocks when artifact signature verifier is missing")
    void shouldBlockPublishWhenArtifactSignatureVerifierIsMissing() throws IOException {
        Path artifactRoot = tempDir.resolve("artifacts");
        enableArtifactSignatureVerification(artifactRoot, null);
        seedCandidate(DRAFT_KEY);
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(artifactRoot, null);
        setOpaqueSignatures(evidence);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                DRAFT_KEY,
                "idem:publish:missing-signature-verifier",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertEquals(ExperienceRecipeFailureStage.GATE_VALIDATION.wireValue(), blocked.getFailureStage());
        assertTrue(blocked.getMessage().contains("signature verifier is not configured"));
        assertEquals("candidate", statusOf(DRAFT_KEY));
        assertEquals(3, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("publish_validated blocks invalid artifact signature")
    void shouldBlockPublishWhenArtifactSignatureIsInvalid() throws Exception {
        Path artifactRoot = tempDir.resolve("artifacts");
        TestSigningMaterial signing = signingMaterial("recipe-evidence-key", "tenant-a", "data_analyst");
        enableArtifactSignatureVerification(artifactRoot, signing.verifier());
        seedCandidate(DRAFT_KEY, "odoo", "tenant-a", "crm:read", "data_analyst");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(artifactRoot, null);
        signArtifacts(evidence, signing, signatureContext(DRAFT_KEY, "odoo", "tenant-a", "data_analyst"));
        List<ExperienceRecipeEvidenceArtifact> artifacts = evidence.getEvidenceArtifacts();
        artifacts.get(0).setArtifactSignature("sig:v1:ed25519:recipe-evidence-key:not-base64***");
        evidence.setEvidenceArtifacts(artifacts);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                DRAFT_KEY,
                "idem:publish:invalid-artifact-signature",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertEquals(ExperienceRecipeFailureStage.GATE_VALIDATION.wireValue(), blocked.getFailureStage());
        assertTrue(blocked.getMessage().contains("signature verification failed"));
        assertEquals("candidate", statusOf(DRAFT_KEY));
        assertEquals(3, count("experience_recipe_registry_event"));
    }

    @Test
    @DisplayName("publish_validated blocks artifact signature when trust key tenant scope mismatches")
    void shouldBlockPublishWhenArtifactSignatureTenantMismatches() throws Exception {
        Path artifactRoot = tempDir.resolve("artifacts");
        TestSigningMaterial signing = signingMaterial("recipe-evidence-key", "tenant-b", "finance_owner");
        enableArtifactSignatureVerification(artifactRoot, signing.verifier());
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(artifactRoot, null);
        signArtifacts(evidence, signing, signatureContext(PUBLISH_KEY, "odoo", "tenant-a", "finance_owner"));

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:signature-tenant-mismatch",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertTrue(blocked.getMessage().contains("tenant mismatch"));
        assertEquals("candidate", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated blocks artifact signature when trust key owner scope mismatches")
    void shouldBlockPublishWhenArtifactSignatureOwnerMismatches() throws Exception {
        Path artifactRoot = tempDir.resolve("artifacts");
        TestSigningMaterial signing = signingMaterial("recipe-evidence-key", "tenant-a", "support_owner");
        enableArtifactSignatureVerification(artifactRoot, signing.verifier());
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(artifactRoot, null);
        signArtifacts(evidence, signing, signatureContext(PUBLISH_KEY, "odoo", "tenant-a", "finance_owner"));

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:signature-owner-mismatch",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertTrue(blocked.getMessage().contains("owner mismatch"));
        assertEquals("candidate", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated blocks artifact signature with unsupported algorithm")
    void shouldBlockPublishWhenArtifactSignatureAlgorithmIsUnsupported() throws Exception {
        Path artifactRoot = tempDir.resolve("artifacts");
        TestSigningMaterial signing = signingMaterial("recipe-evidence-key", "tenant-a", "finance_owner");
        enableArtifactSignatureVerification(artifactRoot, signing.verifier());
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(artifactRoot, null);
        signArtifacts(evidence, signing, signatureContext(PUBLISH_KEY, "odoo", "tenant-a", "finance_owner"));
        List<ExperienceRecipeEvidenceArtifact> artifacts = evidence.getEvidenceArtifacts();
        artifacts.get(0).setArtifactSignature("sig:v1:rsa:recipe-evidence-key:opaque");
        evidence.setEvidenceArtifacts(artifacts);

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:signature-unsupported-algorithm",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertTrue(blocked.getMessage().contains("unsupported signature algorithm"));
        assertEquals("candidate", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated blocks artifact signature signed after trust key expiry")
    void shouldBlockPublishWhenArtifactSignatureKeyIsExpired() throws Exception {
        Path artifactRoot = tempDir.resolve("artifacts");
        TestSigningMaterial signing = signingMaterial(
                "recipe-evidence-key",
                "tenant-a",
                "finance_owner",
                SIGNED_AT.minusSeconds(120),
                SIGNED_AT.minusSeconds(1),
                ExperienceRecipeArtifactTrustKey.Status.ENABLED,
                null);
        enableArtifactSignatureVerification(artifactRoot, signing.verifier());
        seedCandidate(PUBLISH_KEY, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(artifactRoot, null);
        signArtifacts(evidence, signing, signatureContext(PUBLISH_KEY, "odoo", "tenant-a", "finance_owner"));

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                PUBLISH_KEY,
                "idem:publish:signature-expired-key",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertTrue(blocked.getMessage().contains("expired"));
        assertEquals("candidate", statusOf(PUBLISH_KEY));
    }

    @Test
    @DisplayName("publish_validated blocks artifact signature replayed against another recipe context")
    void shouldBlockPublishWhenArtifactSignaturePayloadIsReplayed() throws Exception {
        Path artifactRoot = tempDir.resolve("artifacts");
        String replayKey = "crm_target_month_conversion_window@v1";
        TestSigningMaterial signing = signingMaterial("recipe-evidence-key", "tenant-a", "finance_owner");
        enableArtifactSignatureVerification(artifactRoot, signing.verifier());
        seedCandidate(replayKey, "odoo", "tenant-a", "crm:read,finance:read", "finance_owner");
        ExperienceRecipeGovernanceEvidence evidence = passedEvidenceBackedByFiles(artifactRoot, null);
        signArtifacts(evidence, signing, signatureContext(PUBLISH_KEY, "odoo", "tenant-a", "finance_owner"));

        ExperienceRecipeRegistryResponse blocked = service.mutate(publishRequest(
                replayKey,
                "idem:publish:signature-replay",
                "registry_admin",
                evidence));

        assertEquals(ExperienceRecipeApiResult.BLOCKED, blocked.getApiResult());
        assertEquals(ExperienceRecipeStatus.CANDIDATE, blocked.getStatus());
        assertTrue(blocked.getMessage().contains("invalid Ed25519 signature"));
        assertEquals("candidate", statusOf(replayKey));
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
        seedCandidate(registryKey, null, null, null, null);
    }

    private void seedCandidate(
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

    private void enableArtifactResolution(Path artifactRoot) {
        ExperienceRecipeRegistryProperties properties = new ExperienceRecipeRegistryProperties();
        properties.setRequireArtifactResolution(true);
        properties.setArtifactRoot(artifactRoot.toString());
        service = new ExperienceRecipeRegistryService(
                store,
                new DataSourceTransactionManager(dataSource),
                new FileSystemExperienceRecipeArtifactResolver(properties),
                properties);
    }

    private void enableArtifactSignatureVerification(
            Path artifactRoot,
            ExperienceRecipeArtifactSignatureVerifier signatureVerifier) {
        ExperienceRecipeRegistryProperties properties = new ExperienceRecipeRegistryProperties();
        properties.setRequireArtifactResolution(true);
        properties.setRequireArtifactSignatureVerification(true);
        properties.setArtifactRoot(artifactRoot.toString());
        service = new ExperienceRecipeRegistryService(
                store,
                new DataSourceTransactionManager(dataSource),
                new FileSystemExperienceRecipeArtifactResolver(properties),
                signatureVerifier,
                properties);
    }

    private static ExperienceRecipeRegistryProperties remotePolicyProperties() {
        ExperienceRecipeRegistryProperties properties = new ExperienceRecipeRegistryProperties();
        properties.setRequireArtifactResolution(true);
        properties.getArtifactUriPolicy().setEnabled(true);
        properties.getArtifactUriPolicy().setAllowedUriPrefixes(List.of(
                "https://artifacts.example.com/tenants/{tenant}/owners/{owner}/recipes/{registryKey}/"));
        return properties;
    }

    private static ExperienceRecipeRegistryProperties objectMetadataPolicyProperties() {
        ExperienceRecipeRegistryProperties properties = new ExperienceRecipeRegistryProperties();
        properties.getArtifactObjectMetadataPolicy().setEnabled(true);
        return properties;
    }

    private static ExperienceRecipeRegistryProperties trustedObjectMetadataPolicyProperties() {
        ExperienceRecipeRegistryProperties properties = objectMetadataPolicyProperties();
        properties.setRequireArtifactResolution(true);
        properties.getArtifactObjectMetadataPolicy().setRequireResolvedObjectMetadata(true);
        return properties;
    }

    private static void setOpaqueSignatures(ExperienceRecipeGovernanceEvidence evidence) {
        List<ExperienceRecipeEvidenceArtifact> artifacts = evidence.getEvidenceArtifacts();
        for (ExperienceRecipeEvidenceArtifact artifact : artifacts) {
            artifact.setSignedAt(SIGNED_AT.toString());
            artifact.setArtifactSignature("sig:v1:ed25519:opaque-key:opaque-signature");
        }
        evidence.setEvidenceArtifacts(artifacts);
    }

    private static void signArtifacts(
            ExperienceRecipeGovernanceEvidence evidence,
            TestSigningMaterial signing,
            ExperienceRecipeArtifactSignatureContext context) throws Exception {
        List<ExperienceRecipeEvidenceArtifact> artifacts = evidence.getEvidenceArtifacts();
        for (ExperienceRecipeEvidenceArtifact artifact : artifacts) {
            artifact.setSignedAt(SIGNED_AT.toString());
            artifact.setArtifactSignature(signatureFor(signing.privateKey(), signing.keyId(), context, artifact));
        }
        evidence.setEvidenceArtifacts(artifacts);
    }

    private static String signatureFor(
            PrivateKey privateKey,
            String keyId,
            ExperienceRecipeArtifactSignatureContext context,
            ExperienceRecipeEvidenceArtifact artifact) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(ExperienceRecipeArtifactSignaturePayload.canonicalBytes(context, artifact));
        return "sig:v1:ed25519:" + keyId + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    private static TestSigningMaterial signingMaterial(
            String keyId,
            String tenantId,
            String ownerId) throws Exception {
        return signingMaterial(
                keyId,
                tenantId,
                ownerId,
                SIGNED_AT.minusSeconds(120),
                SIGNED_AT.plusSeconds(120),
                ExperienceRecipeArtifactTrustKey.Status.ENABLED,
                null);
    }

    private static TestSigningMaterial signingMaterial(
            String keyId,
            String tenantId,
            String ownerId,
            Instant validFrom,
            Instant validTo,
            ExperienceRecipeArtifactTrustKey.Status status,
            Instant revokedAt) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = generator.generateKeyPair();
        ExperienceRecipeArtifactTrustKey trustKey = new ExperienceRecipeArtifactTrustKey(
                keyId,
                "ed25519",
                keyPair.getPublic().getEncoded(),
                Set.of(ExperienceRecipeArtifactSignaturePayload.PURPOSE),
                Set.of(tenantId),
                Set.of(ownerId),
                Set.copyOf(requiredArtifactTypes()),
                Set.of("registry_admin"),
                validFrom,
                validTo,
                status,
                revokedAt);
        ExperienceRecipeArtifactTrustStore trustStore =
                new InMemoryExperienceRecipeArtifactTrustStore(List.of(trustKey));
        return new TestSigningMaterial(
                keyId,
                keyPair.getPrivate(),
                new Ed25519ExperienceRecipeArtifactSignatureVerifier(trustStore, SIGNING_CLOCK));
    }

    private static ExperienceRecipeArtifactSignatureContext signatureContext(
            String registryKey,
            String namespace,
            String tenantId,
            String ownerId) {
        String recipeId = registryKey.substring(0, registryKey.indexOf('@'));
        String recipeVersion = registryKey.substring(registryKey.indexOf('@') + 1);
        return new ExperienceRecipeArtifactSignatureContext(
                namespace,
                tenantId,
                registryKey,
                recipeId.replace("_finance_owner", ""),
                recipeVersion,
                ownerId);
    }

    private ExperienceRecipeGovernanceEvidence passedEvidenceBackedByFiles(
            Path artifactRoot,
            String missingArtifactType) throws IOException {
        ExperienceRecipeGovernanceEvidence evidence = ExperienceRecipeGovernanceEvidence.passed();
        List<ExperienceRecipeEvidenceArtifact> artifacts = new ArrayList<>();
        for (String artifactType : requiredArtifactTypes()) {
            String artifactUri = "foggy://experience-recipes/evidence/" + artifactType.replace('_', '-');
            String artifactContent = "artifact-content:" + artifactType;
            if (!artifactType.equals(missingArtifactType)) {
                writeArtifact(artifactRoot, artifactUri, artifactContent);
            }
            artifacts.add(ExperienceRecipeEvidenceArtifact.of(
                    artifactType,
                    artifactUri,
                    ExperienceRecipeArtifactHash.sha256(artifactContent),
                    "registry_admin"));
        }
        evidence.setEvidenceArtifacts(artifacts);
        return evidence;
    }

    private static ExperienceRecipeGovernanceEvidence passedEvidenceBackedByRemoteArtifacts() {
        ExperienceRecipeGovernanceEvidence evidence = ExperienceRecipeGovernanceEvidence.passed();
        List<ExperienceRecipeEvidenceArtifact> artifacts = new ArrayList<>();
        for (String artifactType : requiredArtifactTypes()) {
            byte[] content = remoteArtifactContent(artifactType);
            artifacts.add(ExperienceRecipeEvidenceArtifact.of(
                    artifactType,
                    "https://artifacts.example.com/evidence/" + artifactType.replace('_', '-'),
                    ExperienceRecipeArtifactHash.sha256(content),
                    "registry_admin"));
        }
        evidence.setEvidenceArtifacts(artifacts);
        return evidence;
    }

    private static ExperienceRecipeGovernanceEvidence passedEvidenceBackedByScopedRemoteArtifacts(
            String tenantId,
            String ownerId,
            String registryKey) {
        ExperienceRecipeGovernanceEvidence evidence = ExperienceRecipeGovernanceEvidence.passed();
        List<ExperienceRecipeEvidenceArtifact> artifacts = new ArrayList<>();
        for (String artifactType : requiredArtifactTypes()) {
            byte[] content = remoteArtifactContent(artifactType);
            artifacts.add(ExperienceRecipeEvidenceArtifact.of(
                    artifactType,
                    remoteArtifactUri(tenantId, ownerId, registryKey, artifactType),
                    ExperienceRecipeArtifactHash.sha256(content),
                    "registry_admin"));
        }
        evidence.setEvidenceArtifacts(artifacts);
        return evidence;
    }

    private static void addObjectMetadata(
            ExperienceRecipeGovernanceEvidence evidence,
            String namespace,
            String tenantId,
            String ownerId,
            String registryKey) {
        List<ExperienceRecipeEvidenceArtifact> artifacts = evidence.getEvidenceArtifacts();
        String recipeId = registryKey.substring(0, registryKey.indexOf('@'));
        String recipeVersion = registryKey.substring(registryKey.indexOf('@') + 1);
        for (ExperienceRecipeEvidenceArtifact artifact : artifacts) {
            artifact.setObjectVersion("v-" + artifact.getArtifactType());
            artifact.setObjectEtag("etag-" + artifact.getArtifactType());
            artifact.setObjectMetadata(objectMetadataFor(
                    artifact,
                    namespace,
                    tenantId,
                    ownerId,
                    registryKey,
                    recipeId,
                    recipeVersion));
        }
        evidence.setEvidenceArtifacts(artifacts);
    }

    private static ExperienceRecipeArtifactResolver trustedObjectMetadataResolver(
            String namespace,
            String tenantId,
            String ownerId,
            String registryKey) {
        return trustedObjectMetadataResolver(namespace, tenantId, ownerId, registryKey, null);
    }

    private static ExperienceRecipeArtifactResolver trustedObjectMetadataResolver(
            String namespace,
            String tenantId,
            String ownerId,
            String registryKey,
            AtomicInteger calls) {
        return new ExperienceRecipeArtifactResolver() {
            @Override
            public Optional<byte[]> resolve(ExperienceRecipeEvidenceArtifact artifact) {
                return Optional.of(remoteArtifactContent(artifact.getArtifactType()));
            }

            @Override
            public Optional<ExperienceRecipeArtifactResolution> resolveArtifact(
                    ExperienceRecipeEvidenceArtifact artifact) {
                if (calls != null) {
                    calls.incrementAndGet();
                }
                String recipeId = registryKey.substring(0, registryKey.indexOf('@'));
                String recipeVersion = registryKey.substring(registryKey.indexOf('@') + 1);
                return Optional.of(ExperienceRecipeArtifactResolution.of(
                        remoteArtifactContent(artifact.getArtifactType()),
                        "v-" + artifact.getArtifactType(),
                        "etag-" + artifact.getArtifactType(),
                        objectMetadataFor(
                                artifact,
                                namespace,
                                tenantId,
                                ownerId,
                                registryKey,
                                recipeId,
                                recipeVersion)));
            }
        };
    }

    private static Map<String, String> objectMetadataFor(
            ExperienceRecipeEvidenceArtifact artifact,
            String namespace,
            String tenantId,
            String ownerId,
            String registryKey,
            String recipeId,
            String recipeVersion) {
        return Map.of(
                "namespace", namespace,
                "tenant", tenantId,
                "owner", ownerId,
                "registryKey", registryKey,
                "canonicalRecipeId", recipeId.replace("_finance_owner", ""),
                "version", recipeVersion,
                "artifactType", artifact.getArtifactType(),
                "artifactHash", artifact.getArtifactHash());
    }

    private static String remoteArtifactUri(
            String tenantId,
            String ownerId,
            String registryKey,
            String artifactType) {
        return "https://artifacts.example.com/tenants/"
                + tenantId
                + "/owners/"
                + ownerId
                + "/recipes/"
                + registryKey
                + "/evidence/"
                + artifactType.replace('_', '-');
    }

    private static byte[] remoteArtifactContent(String artifactType) {
        return ("remote-artifact-content:" + artifactType).getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> requiredArtifactTypes() {
        return List.of(
                ExperienceRecipeGovernanceEvidence.OWNER_SIGNOFF_ARTIFACT,
                ExperienceRecipeGovernanceEvidence.SCHEMA_VALIDATION_ARTIFACT,
                ExperienceRecipeGovernanceEvidence.VALIDATION_REPORT_ARTIFACT,
                ExperienceRecipeGovernanceEvidence.POSITIVE_NEGATIVE_EXAMPLES_ARTIFACT,
                ExperienceRecipeGovernanceEvidence.PERMISSION_SCOPE_ARTIFACT);
    }

    private static void writeArtifact(Path artifactRoot, String artifactUri, String content) throws IOException {
        URI uri = URI.create(artifactUri);
        Path artifactPath = artifactRoot.resolve(uri.getAuthority())
                .resolve(stripLeadingSlash(uri.getPath()))
                .normalize();
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, content, StandardCharsets.UTF_8);
    }

    private static String stripLeadingSlash(String value) {
        String stripped = value;
        while (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }
        return stripped;
    }

    private record TestSigningMaterial(
            String keyId,
            PrivateKey privateKey,
            ExperienceRecipeArtifactSignatureVerifier verifier) {
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
