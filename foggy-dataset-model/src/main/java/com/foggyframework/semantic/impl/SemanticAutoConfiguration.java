package com.foggyframework.semantic.impl;

import com.foggyframework.semantic.impl.facade.impl.SemanticFacadeImpl;
import com.foggyframework.semantic.impl.loader.SemanticLoaderImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
//@ComponentScan("com.foggyframework.semantic.impl")
public class SemanticAutoConfiguration {
    @Bean
    public SemanticLoaderImpl semanticLoader() {

        return new SemanticLoaderImpl();
    }

    @Bean
    public SemanticFacadeImpl semanticFacade(SemanticLoaderImpl semanticLoader) {

        return new SemanticFacadeImpl(semanticLoader);
    }
}
