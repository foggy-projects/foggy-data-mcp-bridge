package com.foggyframework.odoo.bridge;

import com.foggyframework.core.annotates.EnableFoggyFramework;
import com.foggyframework.dataset.db.model.DbModelAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Foggy Odoo Bridge Auto Configuration
 *
 * <p>Provides built-in Odoo data models for Foggy MCP Server.
 * When this module is included, the following Odoo models are available:
 *
 * <h3>Available Query Models:</h3>
 * <ul>
 *   <li>OdooSaleOrderQueryModel - Sales analysis</li>
 *   <li>OdooSaleOrderLineQueryModel - Sales line items</li>
 *   <li>OdooPurchaseOrderQueryModel - Purchase analysis</li>
 *   <li>OdooAccountMoveQueryModel - Invoice analysis</li>
 *   <li>OdooStockPickingQueryModel - Inventory transfers</li>
 *   <li>OdooHrEmployeeQueryModel - HR analytics</li>
 *   <li>OdooResPartnerQueryModel - Partner/Customer analysis</li>
 *   <li>OdooResCompanyQueryModel - Company hierarchy</li>
 *   <li>OdooCrmLeadQueryModel - CRM leads</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>
 * // In your Spring Boot application
 * &#64;SpringBootApplication
 * &#64;EnableFoggyFramework(bundleName = "my-odoo-app")
 * public class MyApp {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApp.class, args);
 *     }
 * }
 * </pre>
 *
 * <h3>Data Source Configuration:</h3>
 * <p>Use the DataSource API to configure Odoo database connection at runtime:
 * <pre>
 * POST /api/v1/datasource
 * {
 *   "name": "odoo",
 *   "host": "localhost",
 *   "port": 5432,
 *   "database": "odoo",
 *   "username": "odoo",
 *   "password": "password"
 * }
 * </pre>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@AutoConfiguration(after = DbModelAutoConfiguration.class)
@ConditionalOnProperty(prefix = "foggy.odoo", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableFoggyFramework(bundleName = "odoo", namespace = "odoo")
public class OdooBridgeAutoConfiguration {

}
