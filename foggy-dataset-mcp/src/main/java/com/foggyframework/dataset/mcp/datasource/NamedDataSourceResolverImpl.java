package com.foggyframework.dataset.mcp.datasource;

import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.DatasourceBindingResolver;
import com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.model.spi.ProcessLocalDefaultDataSourceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Implementation of NamedDataSourceResolver using DataSourceManager.
 *
 * <p>This bridges the DataSource API (used by Odoo to register its database)
 * with the TM model loading system (which uses dataSourceName to reference it).
 *
 * <h3>Flow:</h3>
 * <ol>
 *   <li>Odoo calls POST /api/v1/datasource to register "odoo" data source</li>
 *   <li>TM model defines dataSourceName: "odoo"</li>
 *   <li>Model loader calls NamedDataSourceResolver.resolve("odoo")</li>
 *   <li>This implementation returns the DataSource from DataSourceManager</li>
 * </ol>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NamedDataSourceResolverImpl implements NamedDataSourceResolver, DatasourceBindingResolver,
        ProcessLocalDefaultDataSourceResolver {

    private final DataSourceManager dataSourceManager;

    @Override
    public DataSource resolve(String name) {
        ResolvedDatasourceBinding binding = resolveBinding(name);
        if (binding == null) {
            log.debug("Named data source '{}' not found", name);
            return null;
        }
        return binding.dataSource();
    }

    @Override
    public ResolvedDatasourceBinding resolveBinding(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return dataSourceManager.resolveBinding(name);
    }

    @Override
    public ResolvedDatasourceBinding resolveProcessLocalDefaultBinding() {
        return dataSourceManager.resolveBinding("default");
    }

    @Override
    public BindingCurrentness currentness(DatasourceBindingIdentity identity) {
        return dataSourceManager.currentness(identity);
    }

    @Override
    public <T> T publishIfCurrent(
            Collection<DatasourceBindingIdentity> identities,
            Supplier<T> publication
    ) {
        return dataSourceManager.publishIfCurrent(identities, publication);
    }

    @Override
    public boolean isConfigured(String name) {
        return dataSourceManager.isConfigured(name);
    }
}
