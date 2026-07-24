package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.model.engine.compose.compilation.ComposeCompileException;
import com.foggyframework.dataset.model.engine.compose.authority.AuthorityBindingResolver;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.runtime.ScriptRuntime;
import com.foggyframework.dataset.model.engine.compose.sandbox.ComposeSandboxViolationException;
import com.foggyframework.dataset.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolutionException;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * MCP tool for executing Compose Query scripts (M7).
 *
 * <p>Name: {@code dataset.compose_script}. Accepts a single parameter
 * {@code script} (String) and returns a success/error payload:</p>
 *
 * <ul>
 *   <li>Success: {@code {status: "success", data: {value, sql, params}}}</li>
 *   <li>Error: {@code {status: "error", data: {error_code, phase, message, model?}}}</li>
 * </ul>
 *
 * <p>Error families (no new error codes — 100% reuse of prior milestones):</p>
 * <ul>
 *   <li>{@link AuthorityResolutionException} → phase=permission-resolve</li>
 *   <li>{@link ComposeSchemaException} → phase=schema-derive</li>
 *   <li>{@link ComposeCompileException} → phase=compile</li>
 *   <li>{@link ComposeSandboxViolationException} → phase=compile</li>
 *   <li>RuntimeException (execute phase) → phase=execute</li>
 *   <li>RuntimeException (host misconfig) → phase=internal</li>
 *   <li>Other Exception → phase=internal</li>
 * </ul>
 *
 * <p>Only registered when a {@code composeAuthorityResolverFactory} bean
 * is available — hosts that do not implement embedded-mode authority
 * resolution will not activate this tool.</p>
 *
 * @implNote since 8.2.0.beta: this is the tool for multi-model query /
 * union / join composition; walks the M6 full SQL compilation + authority
 * pipeline.
 *
 * @since 8.2.0.beta
 */
@Slf4j
@Component
@ConditionalOnBean(name = "composeAuthorityResolverFactory")
public class ComposeScriptTool implements McpTool {

    static final String AUTHORITY_BINDING_ARGUMENT = "__foggyAuthorityBinding";
    static final String REMOTE_COMPOSE_HEADER = "X-Foggy-Remote-Compose";
    static final String REMOTE_COMPOSE_NAMESPACE = "odoo";

    private final SemanticQueryServiceV3 semanticService;
    private final Function<ToolExecutionContext, AuthorityResolver> resolverFactory;
    private final String defaultDialect;

    public ComposeScriptTool(
            SemanticQueryServiceV3 semanticService,
            @Qualifier("composeAuthorityResolverFactory")
            Function<ToolExecutionContext, AuthorityResolver> resolverFactory,
            @Value("${foggy.compose.dialect:mysql}") String defaultDialect) {
        this.semanticService = Objects.requireNonNull(semanticService,
                "semanticService must not be null");
        this.resolverFactory = Objects.requireNonNull(resolverFactory,
                "resolverFactory must not be null");
        this.defaultDialect = defaultDialect != null ? defaultDialect : "mysql";
    }

    @Override
    public String getName() {
        return "dataset.compose_script";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return EnumSet.of(ToolCategory.QUERY);
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext toolCtx) {
        Map<String, Object> visibleArguments = arguments == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(arguments);
        Object authorityBinding = visibleArguments.remove(AUTHORITY_BINDING_ARGUMENT);
        removeHostPrivateArgument(arguments);

        String script = (String) visibleArguments.get("script");
        if (script == null || script.isBlank()) {
            return errorPayload("missing-script", "internal",
                    "parameter 'script' is required and must be non-blank", null);
        }
        try {
            AuthorityResolver resolver = isRemoteCompose(toolCtx)
                    ? new AuthorityBindingResolver(authorityBinding, REMOTE_COMPOSE_NAMESPACE)
                    : resolverFactory.apply(toolCtx);
            if (resolver == null) {
                return errorPayload("host-misconfig", "internal",
                        "authority resolver factory returned null for this ToolExecutionContext", null);
            }
            ComposeQueryContext ctx = ContextBridge.toComposeContext(toolCtx, resolver);
            ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                    script, ctx, semanticService, defaultDialect);
            Object value = withEmptyResultSemantic(result.value());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("value", value);
            data.put("sql", result.sql() != null ? result.sql() : "");
            data.put("params", result.params() != null ? result.params() : List.of());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "success");
            resp.put("data", data);
            return resp;
        } catch (AuthorityResolutionException e) {
            log.warn("compose_script permission-resolve error: {}", e.getMessage());
            return errorPayload(e.code(), "permission-resolve", e.getMessage(), e.modelInvolved());
        } catch (ComposeSchemaException e) {
            return errorPayload(e.code(), "schema-derive", e.getMessage(), e.offendingField());
        } catch (ComposeCompileException e) {
            return errorPayload(e.code(), "compile", e.getMessage(), null);
        } catch (ComposeSandboxViolationException e) {
            log.warn("compose_script sandbox violation: {}", e.getMessage());
            return errorPayload(e.code(), "compile", e.getMessage(), null);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.startsWith("Plan execution failed at execute phase:")) {
                return errorPayload("execute-phase-error", "execute", msg, null);
            }
            if (msg.contains("requires an ambient ComposeRuntimeBundle")
                    || msg.contains("semanticService unbound")) {
                return errorPayload("host-misconfig", "internal", msg, null);
            }
            return errorPayload("internal-error", "internal", msg, null);
        } catch (Exception e) {
            log.error("compose_script unexpected error", e);
            return errorPayload("internal-error", "internal",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), null);
        }
    }

    private static boolean isRemoteCompose(ToolExecutionContext toolCtx) {
        return toolCtx != null && "1".equals(toolCtx.getHeader(REMOTE_COMPOSE_HEADER));
    }

    private static void removeHostPrivateArgument(Map<String, Object> arguments) {
        if (arguments == null || !arguments.containsKey(AUTHORITY_BINDING_ARGUMENT)) {
            return;
        }
        try {
            arguments.remove(AUTHORITY_BINDING_ARGUMENT);
        } catch (UnsupportedOperationException ignored) {
            // Some tests and embedded callers pass immutable maps; the script
            // still receives only the sanitized copy above.
        }
    }

    @SuppressWarnings("unchecked")
    private static Object withEmptyResultSemantic(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return value;
        }
        Map<String, Object> map = raw instanceof LinkedHashMap<?, ?>
                ? (Map<String, Object>) raw
                : new LinkedHashMap<>((Map<String, Object>) raw);
        if (map.containsKey("semantic")) {
            return map;
        }
        Object plans = map.get("plans");
        boolean emptyPlans = plans instanceof List<?> list && list.isEmpty();
        if (!emptyPlans) {
            return map;
        }
        Map<String, Object> semantic = new LinkedHashMap<>();
        semantic.put("emptyResult", true);
        semantic.put("emptyReason", "NO_MATCHING_ROWS_AFTER_COMPOSE");
        semantic.put("shouldAnswerDirectly", true);
        map.put("semantic", semantic);
        return map;
    }

    private static Map<String, Object> errorPayload(String code, String phase,
                                                    String message, String model) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("error_code", code);
        data.put("phase", phase);
        data.put("message", message);
        if (model != null) data.put("model", model);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "error");
        resp.put("data", data);
        return resp;
    }
}
