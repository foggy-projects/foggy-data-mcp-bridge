package com.foggyframework.dataset.mcp.experience;

import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import com.foggyframework.dataset.mcp.service.ToolConfigLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExperienceRecipeSearchTool wiring")
class ExperienceRecipeSearchToolWiringTest {
    private static final String PUBLISHED_KEY = "crm_source_funnel_and_stage_dropoff_dashboard@v1";
    private static final String CANDIDATE_KEY = "crm_source_funnel_and_stage_dropoff_dashboard@draft";

    @Mock
    private ToolConfigLoader toolConfigLoader;

    @Test
    @DisplayName("dispatcher should register and execute dataset.search_experience_recipes")
    void shouldRegisterAndExecuteThroughDispatcher() {
        ExperienceRecipeRegistryService registryService =
                new ExperienceRecipeRegistryService(new InMemoryExperienceRecipeRegistryStore(), null);
        seedPublished(registryService, PUBLISHED_KEY, "crm_single_model_funnel", "DSL_CTE");
        seedCandidate(registryService, CANDIDATE_KEY, "crm_single_model_funnel", "DSL_CTE");

        when(toolConfigLoader.isEnabled(anyString())).thenReturn(true);
        when(toolConfigLoader.getDescription(anyString())).thenReturn("Search validated experience recipes");
        when(toolConfigLoader.getSchema(anyString())).thenReturn(Map.of("type", "object"));

        ExperienceRecipeSearchTool searchTool = new ExperienceRecipeSearchTool(registryService);
        McpToolDispatcher dispatcher = new McpToolDispatcher(List.of(searchTool), toolConfigLoader);
        dispatcher.init();

        assertTrue(dispatcher.hasTool("dataset.search_experience_recipes"));
        assertEquals("dataset.search_experience_recipes", dispatcher.getToolDefinitions().get(0).get("name"));

        Object raw = dispatcher.executeTool(
                "dataset.search_experience_recipes",
                Map.of("businessType", "crm_single_model_funnel", "route", "DSL_CTE", "limit", 10),
                "trace-exp-recipe",
                "req-exp-recipe",
                null,
                "business_owner",
                "tenant-a");

        Map<String, Object> result = asMap(raw);
        Map<String, Object> data = asMap(result.get("data"));

        assertEquals(200, result.get("code"));
        assertEquals("tenant-a", result.get("namespace"));
        assertEquals("READ_OK", data.get("apiResult"));
        assertEquals(List.of(PUBLISHED_KEY), data.get("returnedRegistryKeys"));
    }

    @Test
    @DisplayName("dispatcher should pass context governance into experience recipe search")
    void shouldSearchWithContextGovernanceThroughDispatcher() {
        ExperienceRecipeRegistryService registryService =
                new ExperienceRecipeRegistryService(new InMemoryExperienceRecipeRegistryStore(), null);
        seedPublished(
                registryService,
                PUBLISHED_KEY,
                "crm_single_model_funnel",
                "DSL_CTE",
                "odoo",
                "tenant-a",
                "crm:read,finance:read",
                "finance_owner");
        seedPublished(
                registryService,
                "crm_target_month_conversion_window@v1",
                "crm_single_model_funnel",
                "DSL_CTE",
                "odoo",
                "tenant-b",
                "crm:read",
                "finance_owner");

        when(toolConfigLoader.isEnabled(anyString())).thenReturn(true);

        McpToolDispatcher dispatcher = new McpToolDispatcher(
                List.of(new ExperienceRecipeSearchTool(registryService)),
                toolConfigLoader);
        dispatcher.init();

        Object raw = dispatcher.executeTool(
                "dataset.search_experience_recipes",
                Map.of("businessType", "crm_single_model_funnel", "route", "DSL_CTE", "limit", 10),
                "trace-exp-recipe-governance",
                "req-exp-recipe-governance",
                null,
                "business_owner",
                "odoo",
                Map.of(
                        "X-Tenant-Id", "tenant-a",
                        "X-Roles", "crm:read,finance:read",
                        "X-Recipe-Owner-Roles", "finance_owner"));

        Map<String, Object> result = asMap(raw);
        Map<String, Object> data = asMap(result.get("data"));

        assertEquals("tenant-a", result.get("tenantId"));
        assertEquals(List.of(PUBLISHED_KEY), data.get("returnedRegistryKeys"));
        assertEquals("selected", data.get("governanceDecision"));
        assertEquals(Map.of("tenant_mismatch", 1), data.get("governanceFilteredCounts"));
    }

    @Test
    @DisplayName("dispatcher should manage experience recipe lifecycle through registry admin context")
    void shouldManageLifecycleThroughDispatcher() {
        ExperienceRecipeRegistryService registryService =
                new ExperienceRecipeRegistryService(new InMemoryExperienceRecipeRegistryStore(), null);

        when(toolConfigLoader.isEnabled(anyString())).thenReturn(true);

        McpToolDispatcher dispatcher = new McpToolDispatcher(
                List.of(
                        new ExperienceRecipeRegistryManageTool(registryService),
                        new ExperienceRecipeSearchTool(registryService)),
                toolConfigLoader);
        dispatcher.init();

        assertTrue(dispatcher.hasTool("dataset.manage_experience_recipe_registry"));

        dispatcher.executeTool(
                "dataset.manage_experience_recipe_registry",
                mapOfEntries(
                        entry("operation", "create_draft_stub"),
                        entry("registryKey", PUBLISHED_KEY),
                        entry("recipeId", "crm_source_funnel_and_stage_dropoff_dashboard"),
                        entry("recipeVersion", "v1"),
                        entry("canonicalRecipeId", "crm_source_funnel_and_stage_dropoff_dashboard"),
                        entry("title", "CRM source funnel and stage dropoff dashboard"),
                        entry("businessType", "crm_single_model_funnel"),
                        entry("route", "DSL_CTE"),
                        entry("namespaceScope", "odoo"),
                        entry("tenantScope", "tenant-a"),
                        entry("permissionTags", "crm:read,finance:read"),
                        entry("ownerRole", "finance_owner"),
                        entry("expectedFromStatus", "none"),
                        entry("expectedFromActiveForDiscovery", false),
                        entry("idempotencyKey", "idem:tool:create")),
                "trace-manage-create",
                "req-manage-create",
                null,
                "registry_admin",
                "odoo");
        dispatcher.executeTool(
                "dataset.manage_experience_recipe_registry",
                Map.of(
                        "operation", "promote_draft_to_candidate",
                        "registryKey", PUBLISHED_KEY,
                        "expectedFromStatus", "draft",
                        "expectedFromActiveForDiscovery", false,
                        "idempotencyKey", "idem:tool:promote"),
                "trace-manage-promote",
                "req-manage-promote",
                null,
                "registry_admin",
                "odoo");
        Object publishedRaw = dispatcher.executeTool(
                "dataset.manage_experience_recipe_registry",
                Map.of(
                        "operation", "publish_validated",
                        "registryKey", PUBLISHED_KEY,
                        "expectedFromStatus", "candidate",
                        "expectedFromActiveForDiscovery", false,
                        "idempotencyKey", "idem:tool:publish",
                        "governanceEvidence", Map.of(
                                "ownerSignoffStatus", "passed",
                                "schemaValidationStatus", "passed",
                                "validationEvidenceStatus", "passed",
                                "positiveNegativeExamplesStatus", "passed",
                                "permissionScopeStatus", "passed",
                                "evidenceArtifacts", evidenceArtifactArgs())),
                "trace-manage-publish",
                "req-manage-publish",
                null,
                "registry_admin",
                "odoo");

        Map<String, Object> published = asMap(publishedRaw);
        Map<String, Object> publishData = asMap(published.get("data"));
        assertEquals("UPDATED", publishData.get("apiResult"));
        assertEquals("validated", publishData.get("status"));
        assertEquals(true, publishData.get("discoverable"));
        assertEquals(5, ((List<?>) publishData.get("evidenceArtifacts")).size());

        Object searchedRaw = dispatcher.executeTool(
                "dataset.search_experience_recipes",
                Map.of("businessType", "crm_single_model_funnel", "route", "DSL_CTE", "limit", 10),
                "trace-manage-search",
                "req-manage-search",
                null,
                "business_owner",
                "odoo",
                Map.of(
                        "X-Tenant-Id", "tenant-a",
                        "X-Roles", "crm:read,finance:read",
                        "X-Recipe-Owner-Roles", "finance_owner"));
        Map<String, Object> searched = asMap(searchedRaw);
        Map<String, Object> searchData = asMap(searched.get("data"));
        assertEquals(List.of(PUBLISHED_KEY), searchData.get("returnedRegistryKeys"));
    }

    @Test
    @DisplayName("manage tool should reject non registry admin actor")
    void shouldRejectManageWithoutRegistryAdminRole() {
        ExperienceRecipeRegistryService registryService =
                new ExperienceRecipeRegistryService(new InMemoryExperienceRecipeRegistryStore(), null);
        ExperienceRecipeRegistryManageTool manageTool = new ExperienceRecipeRegistryManageTool(registryService);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> manageTool.execute(
                Map.of(
                        "operation", "create_draft_stub",
                        "registryKey", PUBLISHED_KEY,
                        "recipeId", "crm_source_funnel_and_stage_dropoff_dashboard",
                        "recipeVersion", "v1",
                        "idempotencyKey", "idem:tool:forbidden"),
                null));

        assertTrue(error.getMessage().contains("registry_admin"));
    }

    private void seedPublished(
            ExperienceRecipeRegistryService registryService,
            String registryKey,
            String businessType,
            String route) {
        seedPublished(registryService, registryKey, businessType, route, null, null, null, null);
    }

    private void seedPublished(
            ExperienceRecipeRegistryService registryService,
            String registryKey,
            String businessType,
            String route,
            String namespaceScope,
            String tenantScope,
            String permissionTags,
            String ownerRole) {
        seedCandidate(registryService, registryKey, businessType, route,
                namespaceScope, tenantScope, permissionTags, ownerRole);
        registryService.mutate(publishRequest(registryKey, "idem:publish:" + registryKey));
    }

    private void seedCandidate(
            ExperienceRecipeRegistryService registryService,
            String registryKey,
            String businessType,
            String route) {
        seedCandidate(registryService, registryKey, businessType, route, null, null, null, null);
    }

    private void seedCandidate(
            ExperienceRecipeRegistryService registryService,
            String registryKey,
            String businessType,
            String route,
            String namespaceScope,
            String tenantScope,
            String permissionTags,
            String ownerRole) {
        registryService.mutate(draftRequest(registryKey, "idem:create:" + registryKey, businessType, route,
                namespaceScope, tenantScope, permissionTags, ownerRole));
        registryService.mutate(promoteRequest(registryKey, "idem:promote:" + registryKey));
    }

    private ExperienceRecipeRegistryMutationRequest draftRequest(
            String registryKey,
            String idempotencyKey,
            String businessType,
            String route) {
        return draftRequest(registryKey, idempotencyKey, businessType, route, null, null, null, null);
    }

    private ExperienceRecipeRegistryMutationRequest draftRequest(
            String registryKey,
            String idempotencyKey,
            String businessType,
            String route,
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
        request.setCanonicalRecipeId(request.getRecipeId());
        request.setTitle("Experience recipe " + request.getRecipeId());
        request.setBusinessType(businessType);
        request.setRoute(route);
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

    private ExperienceRecipeRegistryMutationRequest publishRequest(String registryKey, String idempotencyKey) {
        ExperienceRecipeRegistryMutationRequest request = baseRequest(
                ExperienceRecipeRegistryOperation.PUBLISH_VALIDATED,
                registryKey,
                idempotencyKey,
                "registry_admin");
        request.setExpectedFromStatus(ExperienceRecipeStatus.CANDIDATE);
        request.setExpectedFromActiveForDiscovery(false);
        request.setGovernanceEvidence(ExperienceRecipeGovernanceEvidence.passed());
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<String, Object>) value;
    }

    @SafeVarargs
    private static Map<String, Object> mapOfEntries(Map.Entry<String, Object>... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static Map.Entry<String, Object> entry(String key, Object value) {
        return Map.entry(key, value);
    }

    private static List<Map<String, Object>> evidenceArtifactArgs() {
        return List.of(
                evidenceArtifactArg("owner_signoff"),
                evidenceArtifactArg("schema_validation"),
                evidenceArtifactArg("validation_report"),
                evidenceArtifactArg("positive_negative_examples"),
                evidenceArtifactArg("permission_scope"));
    }

    private static Map<String, Object> evidenceArtifactArg(String artifactType) {
        return Map.of(
                "artifactType", artifactType,
                "artifactUri", "foggy://experience-recipes/evidence/" + artifactType,
                "artifactHash", "sha256:" + artifactType,
                "signedBy", "registry_admin");
    }
}
