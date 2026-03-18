package com.foggyframework.dataset.db.model.spi;

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
public interface NamedDataSourceResolver {

    /**
     * Resolve a named data source
     *
     * @param name Data source name (e.g., "odoo")
     * @return DataSource or null if not found
     */
    DataSource resolve(String name);

    /**
     * Check if a named data source is configured
     *
     * @param name Data source name
     * @return true if configured
     */
    boolean isConfigured(String name);
}