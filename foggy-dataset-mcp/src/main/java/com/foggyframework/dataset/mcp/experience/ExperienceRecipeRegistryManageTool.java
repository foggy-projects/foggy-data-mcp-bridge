package com.foggyframework.dataset.mcp.experience;

import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ExperienceRecipeRegistryManageTool implements McpTool {
    private static final String REQUIRED_ADMIN_ROLE = "registry_admin";

    private final ExperienceRecipeRegistryService registryService;

    public ExperienceRecipeRegistryManageTool(ExperienceRecipeRegistryService registryService) {
        this.registryService = registryService;
    }

    @Override
    public String getName() {
        return "dataset.manage_experience_recipe_registry";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return EnumSet.of(ToolCategory.ADMIN);
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        ExperienceRecipeRegistryMutationRequest request = toMutationRequest(args, context);
        assertRegistryAdmin(request.getActorRole());

        ExperienceRecipeRegistryResponse response = registryService.mutate(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("namespace", context == null ? null : context.getNamespace());
        result.put("actorRole", request.getActorRole());
        result.put("data", response.toResponseMap());
        return result;
    }

    private static ExperienceRecipeRegistryMutationRequest toMutationRequest(
            Map<String, Object> args,
            ToolExecutionContext context) {
        ExperienceRecipeRegistryMutationRequest request = new ExperienceRecipeRegistryMutationRequest();
        request.setOperation(ExperienceRecipeRegistryOperation.fromWireValue(stringValue(args.get("operation"))));
        request.setRegistryKey(stringValue(args.get("registryKey")));
        request.setRecipeId(stringValue(args.get("recipeId")));
        request.setRecipeVersion(stringValue(args.get("recipeVersion")));
        request.setCanonicalRecipeId(stringValue(args.get("canonicalRecipeId")));
        request.setTitle(stringValue(args.get("title")));
        request.setBusinessType(stringValue(args.get("businessType")));
        request.setRoute(stringValue(args.get("route")));
        request.setNamespaceScope(stringValue(args.get("namespaceScope")));
        request.setTenantScope(stringValue(args.get("tenantScope")));
        request.setPermissionTags(stringValue(args.get("permissionTags")));
        request.setOwnerRole(stringValue(args.get("ownerRole")));
        request.setActorRole(resolveActorRole(args, context));
        request.setIdempotencyKey(stringValue(args.get("idempotencyKey")));
        request.setExpectedFromStatus(statusValue(args.get("expectedFromStatus")));
        request.setExpectedFromActiveForDiscovery(booleanValue(args.get("expectedFromActiveForDiscovery")));
        request.setExpectedRecordVersion(longValue(args.get("expectedRecordVersion")));
        request.setReason(stringValue(args.get("reason")));
        request.setGovernanceEvidence(governanceEvidence(args));
        return request;
    }

    private static ExperienceRecipeGovernanceEvidence governanceEvidence(Map<String, Object> args) {
        ExperienceRecipeGovernanceEvidence evidence = new ExperienceRecipeGovernanceEvidence();
        Object nested = args.get("governanceEvidence");
        if (nested instanceof Map<?, ?> map) {
            evidence.setOwnerSignoffStatus(stringValue(map.get("ownerSignoffStatus")));
            evidence.setSchemaValidationStatus(stringValue(map.get("schemaValidationStatus")));
            evidence.setValidationEvidenceStatus(stringValue(map.get("validationEvidenceStatus")));
            evidence.setPositiveNegativeExamplesStatus(stringValue(map.get("positiveNegativeExamplesStatus")));
            evidence.setPermissionScopeStatus(stringValue(map.get("permissionScopeStatus")));
            return evidence;
        }
        evidence.setOwnerSignoffStatus(stringValue(args.get("ownerSignoffStatus")));
        evidence.setSchemaValidationStatus(stringValue(args.get("schemaValidationStatus")));
        evidence.setValidationEvidenceStatus(stringValue(args.get("validationEvidenceStatus")));
        evidence.setPositiveNegativeExamplesStatus(stringValue(args.get("positiveNegativeExamplesStatus")));
        evidence.setPermissionScopeStatus(stringValue(args.get("permissionScopeStatus")));
        return evidence;
    }

    private static String resolveActorRole(Map<String, Object> args, ToolExecutionContext context) {
        return firstNonBlank(
                context == null ? null : context.getHeader("X-Registry-Actor-Role"),
                context == null ? null : context.getUserRole(),
                stringValue(args.get("actorRole")));
    }

    private static void assertRegistryAdmin(String actorRole) {
        if (!REQUIRED_ADMIN_ROLE.equals(normalize(actorRole))) {
            throw new IllegalArgumentException(
                    "dataset.manage_experience_recipe_registry requires registry_admin actor role");
        }
    }

    private static ExperienceRecipeStatus statusValue(Object value) {
        String text = stringValue(value);
        return text == null ? null : ExperienceRecipeStatus.fromWireValue(text);
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = stringValue(value);
        return text == null ? null : Boolean.parseBoolean(text);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = stringValue(value);
        return text == null ? null : Long.parseLong(text);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
