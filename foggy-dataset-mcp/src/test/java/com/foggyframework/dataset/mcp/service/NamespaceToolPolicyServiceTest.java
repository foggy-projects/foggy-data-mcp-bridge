package com.foggyframework.dataset.mcp.service;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.utils.ExpUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamespaceToolPolicyServiceTest {

    @Mock private SystemBundlesContext bundlesContext;
    @Mock private FileFsscriptLoader fileFsscriptLoader;
    @Mock private ApplicationContext applicationContext;
    @Mock private Bundle bundle;
    @Mock private Resource resource;
    @Mock private Fsscript script;
    @Mock private ExpEvaluator evaluator;
    @Mock private FsscriptFunction resolver;

    private NamespaceToolPolicyService service;

    @BeforeEach
    void setUp() {
        service = new NamespaceToolPolicyService(bundlesContext, fileFsscriptLoader, applicationContext);
    }

    @Test
    void missingConfigIsDefaultOpen() {
        when(bundlesContext.findResourceByName("tools.config.js", "wwi", false)).thenReturn(null);
        assertEquals(Set.of("dataset.query_model", "dataset.explain_query"), service.resolveAvailableTools(
                List.of("dataset.query_model", "dataset.explain_query"),
                "wwi", "Bearer token", "ANALYST", "trace-1", Map.of()));
    }

    @Test
    void resolverReceivesTokenAndCanRestrictTools() {
        BundleResource bundleResource = new BundleResource(bundle, resource);
        when(bundlesContext.findResourceByName("tools.config.js", "wwi", false)).thenReturn(bundleResource);
        when(bundle.loadFsscript("tools.config.js", fileFsscriptLoader, true)).thenReturn(script);
        when(script.newInstance(applicationContext)).thenReturn(evaluator);
        when(evaluator.getExportObject("default")).thenReturn(resolver);
        when(resolver.threadSafeAccept(any())).thenReturn(Map.of("enabledTools", List.of("dataset.explain_query")));

        Set<String> available = service.resolveAvailableTools(
                List.of("dataset.query_model", "dataset.explain_query"),
                "wwi", "Bearer demo-territory-southeast", "ANALYST", "trace-2",
                Map.of("X-NS", "wwi"));

        assertEquals(Set.of("dataset.explain_query"), available);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(resolver).threadSafeAccept(context.capture());
        assertEquals("Bearer demo-territory-southeast", context.getValue().get("authorization"));
        assertEquals("wwi", context.getValue().get("namespace"));
        assertEquals(List.of("dataset.query_model", "dataset.explain_query"),
                context.getValue().get("registeredTools"));
    }

    @Test
    void emptyListDisablesAllTools() {
        BundleResource bundleResource = new BundleResource(bundle, resource);
        when(bundlesContext.findResourceByName("tools.config.js", "wwi", false)).thenReturn(bundleResource);
        when(bundle.loadFsscript("tools.config.js", fileFsscriptLoader, true)).thenReturn(script);
        when(script.newInstance(applicationContext)).thenReturn(evaluator);
        when(evaluator.getExportObject("default")).thenReturn(List.of());

        assertTrue(service.resolveAvailableTools(
                List.of("dataset.explain_query"), "wwi", null, "BUSINESS", "trace-3", Map.of()).isEmpty());
    }

    @Test
    void resolverFailureIsDefaultOpen() {
        BundleResource bundleResource = new BundleResource(bundle, resource);
        when(bundlesContext.findResourceByName("tools.config.js", "wwi", false)).thenReturn(bundleResource);
        when(bundle.loadFsscript("tools.config.js", fileFsscriptLoader, true)).thenReturn(script);
        when(script.newInstance(applicationContext)).thenReturn(evaluator);
        doThrow(new IllegalStateException("upstream unavailable")).when(script).eval(eq(evaluator));

        assertEquals(Set.of("dataset.explain_query"), service.resolveAvailableTools(
                List.of("dataset.explain_query"), "wwi", "secret", "ADMIN", "trace-4", Map.of()));
    }

    @Test
    void documentedFunctionExportSyntaxIsSupportedByFsscript() {
        String source = """
                const resolveTools = function(context) {
                    return context.registeredTools;
                };
                export default resolveTools;
                """;
        SimpleFsscriptClosureDefinitionSpace space = new SimpleFsscriptClosureDefinitionSpace();
        FsscriptClosureDefinition definition = space.newFsscriptClosureDefinition();
        Exp exp = ExpUtils.compileEl(definition, source, null);
        ExpEvaluator evaluator = DefaultExpEvaluator.newInstance(null, definition.newFoggyClosure());

        exp.evalResult(evaluator);
        FsscriptFunction function = assertInstanceOf(
                FsscriptFunction.class, evaluator.getExportObject("default"));
        assertEquals(List.of("dataset.explain_query"), function.threadSafeAccept(
                Map.of("registeredTools", List.of("dataset.explain_query"))));
    }

    @Test
    void bundledTemplateParsesAndIsDefaultOpen() throws IOException {
        String source = new ClassPathResource("examples/namespace/tools.config.js")
                .getContentAsString(StandardCharsets.UTF_8);
        SimpleFsscriptClosureDefinitionSpace space = new SimpleFsscriptClosureDefinitionSpace();
        FsscriptClosureDefinition definition = space.newFsscriptClosureDefinition();
        Exp exp = ExpUtils.compileEl(definition, source, null);
        ExpEvaluator evaluator = DefaultExpEvaluator.newInstance(null, definition.newFoggyClosure());

        exp.evalResult(evaluator);
        FsscriptFunction function = assertInstanceOf(
                FsscriptFunction.class, evaluator.getExportObject("default"));
        List<String> registeredTools = List.of("dataset.query_model", "dataset.explain_query");
        assertEquals(registeredTools, function.threadSafeAccept(Map.of(
                "registeredTools", registeredTools,
                "namespace", "wwi",
                "userRole", "ANALYST")));
    }
}
