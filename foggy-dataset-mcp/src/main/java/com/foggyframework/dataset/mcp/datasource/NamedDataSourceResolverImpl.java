package com.foggyframework.dataset.mcp.datasource;

import com.foggyframework.dataset.db.model.spi.NamedDataSourceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

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
public class NamedDataSourceResolverImpl implements NamedDataSourceResolver {

    private final DataSourceManager dataSourceManager;

    @Override
    public DataSource resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        DataSource ds = dataSourceManager.getDataSource(name);
        if (ds == null) {
            log.debug("Named data source '{}' not found", name);
        }
        return ds;
    }

    @Override
    public boolean isConfigured(String name) {
        return dataSourceManager.isConfigured(name);
    }
}