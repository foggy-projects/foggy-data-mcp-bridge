package com.foggyframework.dataset.model.spi;

import com.foggyframework.dataset.model.lifecycle.port.DatasourceBindingResolver;
import com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding;

import javax.sql.DataSource;

/**
 * Named Data Source Resolver
 *
 * <p>Resolves named data sources registered via API.
 * Used by model loaders to support {@code dataSourceName} in TM definitions.
 *
 * <h3>Usage:</h3>
 * <pre>
 * // In TM model:
 * export const model = {
 *     name: 'OdooSaleOrderModel',
 *     tableName: 'sale_order',
 *     dataSourceName: 'odoo',  // Reference to named data source
 *     ...
 * }
 *
 * // The loader will resolve "odoo" to the DataSource registered via API:
 * // POST /api/v1/datasource {name: "odoo", host: "...", ...}
 * </pre>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
public interface NamedDataSourceResolver extends DatasourceBindingResolver {

    /**
     * Resolve a named data source
     *
     * @param name Data source name (e.g., "odoo")
     * @return DataSource or null if not found
     */
    DataSource resolve(String name);

    @Override
    default ResolvedDatasourceBinding resolveBinding(String name) {
        DataSource resolved = resolve(name);
        return resolved == null ? null : ResolvedDatasourceBinding.untracked(resolved);
    }

    /**
     * Resolve a default data source for a runtime namespace.
     *
     * <p>Implementations may use this to let a namespace-level runtime binding
     * provide the default datasource for models that do not explicitly declare
     * {@code dataSourceName} or {@code dataSource}.
     *
     * @param namespace Runtime namespace
     * @return DataSource or null when no namespace default is configured
     */
    default DataSource resolveDefault(String namespace) {
        return null;
    }

    @Override
    default ResolvedDatasourceBinding resolveDefaultBinding(String namespace) {
        DataSource resolved = resolveDefault(namespace);
        return resolved == null ? null : ResolvedDatasourceBinding.untracked(resolved);
    }

    /**
     * Check if a named data source is configured
     *
     * @param name Data source name
     * @return true if configured
     */
    boolean isConfigured(String name);
}
