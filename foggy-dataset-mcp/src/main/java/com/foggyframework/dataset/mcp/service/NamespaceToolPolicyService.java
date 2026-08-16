package com.foggyframework.dataset.mcp.service;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the request-visible MCP tools from an optional namespace-local
 * {@code tools.config.js} FSScript.
 *
 * <p>Missing configuration and resolver failures deliberately fail open. The
 * namespace administrator is the authority that chooses whether to restrict
 * an analysis endpoint. A script can use the existing FSScript {@code post}
 * function and explicitly forward {@code context.authorization} when an
 * upstream policy service is required.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NamespaceToolPolicyService {

    static final String CONFIG_FILE = "tools.config.js";

    private final SystemBundlesContext systemBundlesContext;
    private final FileFsscriptLoader fileFsscriptLoader;
    private final ApplicationContext applicationContext;

    public Set<String> resolveAvailableTools(
            Collection<String> registeredTools,
            String namespace,
            String authorization,
            String userRole,
            String traceId,
            Map<String, String> headers
    ) {
        LinkedHashSet<String> all = new LinkedHashSet<>(registeredTools == null ? List.of() : registeredTools);
        try {
            BundleResource resource = systemBundlesContext.findResourceByName(CONFIG_FILE, namespace, false);
            if (resource == null) {
                return all;
            }

            Fsscript script = resource.getBundle().loadFsscript(CONFIG_FILE, fileFsscriptLoader, true);
            ExpEvaluator evaluator = script.newInstance(applicationContext);
            Map<String, Object> context = buildContext(
                    namespace, authorization, userRole, traceId, headers, new ArrayList<>(all));
            evaluator.setVar("context", context);
            evaluator.setVar("tools", new ArrayList<>(all));
            script.eval(evaluator);

            Object configured = evaluator.getExportObject("default");
            if (configured instanceof FsscriptFunction resolver) {
                configured = resolver.threadSafeAccept(context);
            }
            return normalize(configured, all);
        } catch (RuntimeException ex) {
            // Never log the supplied authorization/header values or script result.
            log.warn("Namespace tool policy failed open: namespace={}, config={}, errorType={}",
                    namespace, CONFIG_FILE, ex.getClass().getSimpleName());
            return all;
        }
    }

    public boolean isAvailable(
            String toolName,
            Collection<String> registeredTools,
            String namespace,
            String authorization,
            String userRole,
            String traceId,
            Map<String, String> headers
    ) {
        return resolveAvailableTools(
                registeredTools, namespace, authorization, userRole, traceId, headers).contains(toolName);
    }

    private Map<String, Object> buildContext(
            String namespace,
            String authorization,
            String userRole,
            String traceId,
            Map<String, String> headers,
            List<String> tools
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("namespace", namespace);
        context.put("authorization", authorization);
        context.put("userRole", userRole);
        context.put("traceId", traceId);
        context.put("headers", headers == null ? Map.of() : new LinkedHashMap<>(headers));
        context.put("registeredTools", tools);
        return context;
    }

    private Set<String> normalize(Object configured, LinkedHashSet<String> registered) {
        if (configured == null) {
            return registered;
        }
        if (configured instanceof Map<?, ?> map) {
            configured = map.containsKey("enabledTools") ? map.get("enabledTools") : map.get("tools");
        }
        if (!(configured instanceof Collection<?> values)) {
            throw new IllegalArgumentException("tools.config.js must return a tool-name array");
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String name = String.valueOf(value);
            if ("*".equals(name)) {
                return registered;
            }
            if (registered.contains(name)) {
                selected.add(name);
            }
        }
        return selected;
    }
}
