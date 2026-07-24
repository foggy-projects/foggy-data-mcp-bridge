package com.foggyframework.dataset.model.starter;

import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.backend.BackendProvider;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;
import com.foggyframework.dataset.model.core.backend.BackendProviderCatalog;
import com.foggyframework.dataset.model.jdbc.JdbcQueryBackendProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Spring assembly for the SPI v2 provider catalog and JDBC compatibility adapter. */
@AutoConfiguration(afterName = "com.foggyframework.dataset.model.DbModelAutoConfiguration")
@ConditionalOnClass({BackendProviderCatalog.class, JdbcQueryBackendProvider.class})
public class ModelBackendAutoConfiguration {

    @Bean
    @ConditionalOnBean(QueryFacade.class)
    @ConditionalOnMissingBean(name = "jdbcQueryBackendProvider")
    public JdbcQueryBackendProvider jdbcQueryBackendProvider(QueryFacade queryFacade) {
        return new JdbcQueryBackendProvider(queryFacade);
    }

    @Bean
    @ConditionalOnMissingBean
    public BackendProviderCatalog backendProviderCatalog(
            ObjectProvider<BackendProvider> providers) {
        return BackendProviderCatalog.of(providers.orderedStream().toList());
    }
}
