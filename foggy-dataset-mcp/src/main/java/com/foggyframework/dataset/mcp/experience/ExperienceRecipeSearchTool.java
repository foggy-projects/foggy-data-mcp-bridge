package com.foggyframework.dataset.mcp.experience;

import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class ExperienceRecipeSearchTool implements McpTool {
    private final ExperienceRecipeRegistryService registryService;

    public ExperienceRecipeSearchTool(ExperienceRecipeRegistryService registryService) {
        this.registryService = registryService;
    }

    @Override
    public String getName() {
        return "dataset.search_experience_recipes";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return EnumSet.of(ToolCategory.METADATA, ToolCategory.QUERY);
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        ExperienceRecipeSearchRequest request = new ExperienceRecipeSearchRequest();
        request.setBusinessType(stringValue(args.get("businessType")));
        request.setRoute(stringValue(args.get("route")));
        request.setNamespace(context == null ? stringValue(args.get("namespace")) : firstNonBlank(
                context.getNamespace(), stringValue(args.get("namespace"))));
        request.setTenantId(firstNonBlank(header(context, "X-Tenant-Id"), stringValue(args.get("tenantId"))));
        request.setPermissionTags(resolvePermissionTags(args, context));
        request.setOwnerRoles(resolveOwnerRoles(args, context));
        request.setLimit(intValue(args.get("limit")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("namespace", context == null ? null : context.getNamespace());
        result.put("tenantId", request.getTenantId());
        result.put("data", registryService.searchDiscoverable(request).toResponseMap());
        return result;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Integer.parseInt(value.toString());
    }

    private static Set<String> resolvePermissionTags(Map<String, Object> args, ToolExecutionContext context) {
        Set<String> values = new LinkedHashSet<>();
        addCsv(values, context == null ? null : context.getUserRole());
        addCsv(values, header(context, "X-Roles"));
        addCsv(values, header(context, "X-Permission-Tags"));
        if (values.isEmpty()) {
            addValue(values, args.get("permissionTags"));
        }
        return values;
    }

    private static Set<String> resolveOwnerRoles(Map<String, Object> args, ToolExecutionContext context) {
        Set<String> values = new LinkedHashSet<>();
        addCsv(values, header(context, "X-Recipe-Owner-Roles"));
        if (values.isEmpty()) {
            addValue(values, args.get("ownerRoles"));
        }
        return values;
    }

    private static void addValue(Set<String> target, Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addCsv(target, item == null ? null : item.toString());
            }
            return;
        }
        addCsv(target, value == null ? null : value.toString());
    }

    private static void addCsv(Set<String> target, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(target::add);
    }

    private static String header(ToolExecutionContext context, String name) {
        return context == null ? null : context.getHeader(name);
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
}
