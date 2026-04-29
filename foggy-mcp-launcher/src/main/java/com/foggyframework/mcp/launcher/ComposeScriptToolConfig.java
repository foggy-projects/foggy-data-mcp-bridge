package com.foggyframework.mcp.launcher;

import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.mcp.tools.ComposeScriptTool;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Development launcher wiring for dataset.compose_script.
 */
@Configuration
public class ComposeScriptToolConfig {

    @Bean("composeAuthorityResolverFactory")
    @ConditionalOnMissingBean(name = "composeAuthorityResolverFactory")
    public Function<ToolExecutionContext, AuthorityResolver> composeAuthorityResolverFactory() {
        return toolContext -> request -> {
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            for (String modelName : request.modelNames()) {
                bindings.put(modelName, ModelBinding.builder().build());
            }
            return AuthorityResolution.builder().bindings(bindings).build();
        };
    }

    @Bean
    @ConditionalOnMissingBean(ComposeScriptTool.class)
    public ComposeScriptTool composeScriptTool(
            SemanticQueryServiceV3 semanticQueryServiceV3,
            @Qualifier("composeAuthorityResolverFactory")
            Function<ToolExecutionContext, AuthorityResolver> resolverFactory,
            @Value("${foggy.compose.dialect:mysql}") String defaultDialect) {
        return new ComposeScriptTool(semanticQueryServiceV3, resolverFactory, defaultDialect);
    }
}
