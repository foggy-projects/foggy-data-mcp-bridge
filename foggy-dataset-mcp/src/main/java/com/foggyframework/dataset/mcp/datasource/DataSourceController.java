package com.foggyframework.dataset.mcp.datasource;

import com.foggyframework.core.ex.RX;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Data Source Configuration API
 *
 * <p>Provides REST API for dynamic data source configuration.
 * Used by Odoo MCP Gateway to configure the Odoo database connection.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>POST /api/v1/datasource - Configure a data source</li>
 *   <li>POST /api/v1/datasource/test - Test connection without storing</li>
 *   <li>GET /api/v1/datasource - List all configured data sources</li>
 *   <li>GET /api/v1/datasource/{name}/status - Get data source status</li>
 *   <li>GET /api/v1/datasource/{name}/test - Test configured data source</li>
 *   <li>DELETE /api/v1/datasource/{name} - Remove a data source</li>
 * </ul>
 *
 * <h3>Authentication:</h3>
 * <p>Requires Bearer token in Authorization header. Token is configured via
 * foggy.auth.token property or FOGGY_AUTH_TOKEN environment variable.
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/datasource")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceManager dataSourceManager;

    /**
     * Configure a data source
     *
     * @param request Configuration request
     * @return Success response
     */
    @PostMapping
    public RX<Void> configure(@RequestBody DataSourceConfigRequest request) {
        RX.hasText(request.getName(), "Data source name is required");

        log.info("Configuring data source: {}", request.getName());

        DataSourceManager.DataSourceConfig config = DataSourceManager.DataSourceConfig.builder()
                .host(request.getHost())
                .port(request.getPort() != null ? request.getPort() : 5432)
                .database(request.getDatabase())
                .username(request.getUsername())
                .password(request.getPassword())
                .driver(request.getDriver())
                .build();

        dataSourceManager.configure(request.getName(), config);

        return RX.ok();
    }

    /**
     * Test connection without storing
     *
     * @param request Connection test request
     * @return Test result
     */
    @PostMapping("/test")
    public RX<DataSourceManager.ConnectionTestResult> testConnection(@RequestBody DataSourceConfigRequest request) {
        log.info("Testing connection: host={}:{} database={}",
                request.getHost(), request.getPort(), request.getDatabase());

        DataSourceManager.DataSourceConfig config = DataSourceManager.DataSourceConfig.builder()
                .host(request.getHost())
                .port(request.getPort() != null ? request.getPort() : 5432)
                .database(request.getDatabase())
                .username(request.getUsername())
                .password(request.getPassword())
                .driver(request.getDriver())
                .build();

        DataSourceManager.ConnectionTestResult result = dataSourceManager.testConnection(config);

        if (result.isSuccess()) {
            log.info("Connection test successful");
        } else {
            log.warn("Connection test failed: {}", result.getMessage());
        }

        return RX.ok(result);
    }

    /**
     * List all configured data sources
     *
     * @return List of data source names
     */
    @GetMapping
    public RX<List<DataSourceStatus>> listAll() {
        Set<String> names = dataSourceManager.getConfiguredNames();
        List<DataSourceStatus> result = new ArrayList<>();

        for (String name : names) {
            DataSourceManager.DataSourceConfig config = dataSourceManager.getConfig(name);
            if (config != null) {
                DataSourceStatus status = new DataSourceStatus();
                status.setName(name);
                status.setConfigured(true);
                status.setHost(config.getHost());
                status.setPort(config.getPort());
                status.setDatabase(config.getDatabase());
                status.setUsername(config.getUsername());
                result.add(status);
            }
        }

        return RX.ok(result);
    }

    /**
     * Get data source status
     *
     * @param name Data source name
     * @return Status information
     */
    @GetMapping("/{name}/status")
    public RX<DataSourceStatus> getStatus(@PathVariable String name) {
        boolean configured = dataSourceManager.isConfigured(name);

        DataSourceStatus status = new DataSourceStatus();
        status.setName(name);
        status.setConfigured(configured);

        if (configured) {
            DataSourceManager.DataSourceConfig config = dataSourceManager.getConfig(name);
            if (config != null) {
                status.setHost(config.getHost());
                status.setPort(config.getPort());
                status.setDatabase(config.getDatabase());
                status.setUsername(config.getUsername());
            }
        }

        return RX.ok(status);
    }

    /**
     * Test a configured data source
     *
     * @param name Data source name
     * @return Test result
     */
    @GetMapping("/{name}/test")
    public RX<DataSourceManager.ConnectionTestResult> testDataSource(@PathVariable String name) {
        log.info("Testing configured data source: {}", name);

        DataSourceManager.ConnectionTestResult result = dataSourceManager.testConnection(name);

        return RX.ok(result);
    }

    /**
     * Remove a data source
     *
     * @param name Data source name
     * @return Success response
     */
    @DeleteMapping("/{name}")
    public RX<Void> remove(@PathVariable String name) {
        log.info("Removing data source: {}", name);

        boolean removed = dataSourceManager.remove(name);

        if (removed) {
            log.info("Data source removed: {}", name);
            return RX.ok();
        } else {
            return RX.<Void>notFound().message("Data source not found: " + name).build();
        }
    }

    /**
     * Data source configuration request
     */
    @lombok.Data
    public static class DataSourceConfigRequest {
        /**
         * Data source name (e.g., "odoo")
         */
        private String name;

        /**
         * Database host
         */
        private String host;

        /**
         * Database port (default: 5432)
         */
        private Integer port;

        /**
         * Database name
         */
        private String database;

        /**
         * Database username
         */
        private String username;

        /**
         * Database password
         */
        private String password;

        /**
         * Driver type: postgresql, mysql, sqlserver (default: postgresql)
         */
        private String driver;
    }

    /**
     * Data source status response
     */
    @lombok.Data
    public static class DataSourceStatus {
        private String name;
        private boolean configured;
        private String host;
        private Integer port;
        private String database;
        private String username;
    }
}