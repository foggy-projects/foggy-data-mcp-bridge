package com.foggyframework.dataset.model.semantic.service;

import com.foggyframework.dataset.model.engine.compose.compilation.ComposeCompileException;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeScriptService;
import com.foggyframework.dataset.model.engine.compose.sandbox.ComposeSandboxViolationException;
import com.foggyframework.dataset.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolutionException;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.port.ComposeCaller;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionException;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionRequest;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionResult;
import com.foggyframework.dataset.model.semantic.port.ComposeOperation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default adapter that keeps Compose implementation types inside model. */
public class DefaultComposeExecutionPort implements ComposeExecutionPort {

    private final SemanticQueryServiceV3 semanticQueryServiceV3;
    private final AuthorityResolver authorityResolver;
    private final String defaultDialect;

    public DefaultComposeExecutionPort(
            SemanticQueryServiceV3 semanticQueryServiceV3,
            ObjectProvider<AuthorityResolver> authorityResolvers,
            @Value("${foggy.compose.dialect:mysql}") String defaultDialect
    ) {
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.authorityResolver = authorityResolvers.orderedStream()
                .findFirst()
                .orElse(DefaultComposeExecutionPort::allowAll);
        this.defaultDialect = defaultDialect != null ? defaultDialect : "mysql";
    }

    @Override
    public ComposeExecutionResult execute(ComposeExecutionRequest request) {
        ComposeScriptService.Mode mode = toEngineMode(request.operation());
        ComposeQueryContext context = ComposeQueryContext.builder()
                .principal(toPrincipal(request.caller()))
                .namespace(request.namespace())
                .traceId(request.traceId())
                .params(request.params())
                .authorityResolver(authorityResolver)
                .build();
        try {
            ComposeScriptService.ComposeScriptResult result = ComposeScriptService.run(
                    ComposeScriptService.ComposeScriptRequest.builder()
                            .mode(mode)
                            .script(request.script())
                            .ctx(context)
                            .semanticService(semanticQueryServiceV3)
                            .dialect(request.dialect() != null ? request.dialect() : defaultDialect)
                            .build());
            return new ComposeExecutionResult(
                    request.operation(),
                    result.valid(),
                    result.executed(),
                    result.value(),
                    result.sql(),
                    result.params(),
                    result.warnings());
        } catch (AuthorityResolutionException e) {
            throw new ComposeExecutionException(
                    ComposeExecutionException.Kind.AUTHORITY,
                    e.code(), e.phase(), e.getMessage(), null, e.modelInvolved(), e);
        } catch (ComposeSandboxViolationException e) {
            throw new ComposeExecutionException(
                    ComposeExecutionException.Kind.SANDBOX,
                    e.code(), e.phase(), e.getMessage(), null, null, e);
        } catch (ComposeSchemaException e) {
            throw new ComposeExecutionException(
                    ComposeExecutionException.Kind.SCHEMA,
                    e.code(), e.phase(), e.getMessage(), e.offendingField(), null, e);
        } catch (ComposeCompileException e) {
            throw new ComposeExecutionException(
                    ComposeExecutionException.Kind.COMPILE,
                    e.code(), e.phase(), e.getMessage(), null, null, e);
        }
    }

    private static Principal toPrincipal(ComposeCaller caller) {
        return Principal.builder()
                .userId(caller.userId())
                .tenantId(caller.tenantId())
                .roles(caller.roles())
                .deptId(caller.deptId())
                .authorizationHint(caller.authorizationHint())
                .policySnapshotId(caller.policySnapshotId())
                .build();
    }

    private static ComposeScriptService.Mode toEngineMode(ComposeOperation operation) {
        return switch (operation) {
            case VALIDATE -> ComposeScriptService.Mode.VALIDATE;
            case PREVIEW -> ComposeScriptService.Mode.PREVIEW;
            case EXECUTE -> ComposeScriptService.Mode.EXECUTE;
        };
    }

    private static AuthorityResolution allowAll(
            com.foggyframework.dataset.model.engine.compose.security.AuthorityRequest request
    ) {
        Map<String, ModelBinding> bindings = new LinkedHashMap<>();
        for (String model : request.modelNames()) {
            bindings.put(model, ModelBinding.builder()
                    .deniedColumns(List.of())
                    .systemSlice(List.of())
                    .build());
        }
        return AuthorityResolution.builder().bindings(bindings).build();
    }
}
