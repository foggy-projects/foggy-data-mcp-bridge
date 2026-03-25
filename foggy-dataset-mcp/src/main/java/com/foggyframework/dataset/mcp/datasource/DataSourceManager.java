package com.foggyframework.dataset.mcp.datasource;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.utils.DataSourceFactory;
import com.foggyframework.dataset.utils.DataSourceFactoryImpl;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic DataSource Manager
 *
 * <p>Manages named data sources that can be configured at runtime via API.
 * Used by Odoo MCP Gateway to configure the Odoo database connection.
 *
 * <p>Configurations are persisted to JSON files in ~/.foggy/datasources/ by default,
 * so they survive service restarts.
 *
 * <h3>Usage:</h3>
 * <pre>
 * // Configure a data source
 * dataSourceManager.configure("odoo", DataSourceConfig.builder()
 *     .host("localhost")
 *     .port(5432)
 *     .database("odoo")
 *     .username("odoo")
 *     .password("password")
 *     .build());
 *
 * // Get the data source
 * DataSource ds = dataSourceManager.getDataSource("odoo");
 * </pre>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceManager {

    private final DataSourceFactory dataSourceFactory = new DataSourceFactoryImpl();
    private final DataSourceConfigPersistence persistence;

    /**
     * Named data sources cache
     */
    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

    /**
     * Named data source configurations
     */
    private final Map<String, DataSourceConfig> configs = new ConcurrentHashMap<>();

    /**
     * Load persisted configurations on startup
     */
    @PostConstruct
    public void init() {
        Map<String, DataSourceConfig> savedConfigs = persistence.loadAll();
        for (Map.Entry<String, DataSourceConfig> entry : savedConfigs.entrySet()) {
            try {
                // Create data source but don't save again
                configureInternal(entry.getKey(), entry.getValue(), false);
                log.info("Restored data source from persisted config: {}", entry.getKey());
            } catch (Exception e) {
                log.error("Failed to restore data source: {}", entry.getKey(), e);
            }
        }
    }

    /**
     * Configure a named data source
     *
     * @param name   Data source name (e.g., "odoo")
     * @param config Data source configuration
     */
    public void configure(String name, DataSourceConfig config) {
        configureInternal(name, config, true);
    }

    /**
     * Internal configure method with optional persistence
     *
     * @param name   Data source name
     * @param config Data source configuration
     * @param persist Whether to persist to file
     */
    private void configureInternal(String name, DataSourceConfig config, boolean persist) {
        RX.hasText(name, "Data source name cannot be empty");
        RX.notNull(config, "Data source config cannot be null");
        RX.hasText(config.getHost(), "Host cannot be empty");
        RX.hasText(config.getDatabase(), "Database cannot be empty");
        RX.hasText(config.getUsername(), "Username cannot be empty");

        log.info("Configuring data source: name={}, host={}:{} database={}",
                name, config.getHost(), config.getPort(), config.getDatabase());

        // Build JDBC URL
        String url = buildJdbcUrl(config);

        // Create data source
        DataSource dataSource = dataSourceFactory.create(url, config.getUsername(), config.getPassword());

        // Store in memory
        dataSources.put(name, dataSource);
        configs.put(name, config);

        // Persist to file
        if (persist) {
            persistence.save(name, config);
        }

        log.info("Data source configured successfully: {}", name);
    }

    /**
     * Get a named data source
     *
     * @param name Data source name
     * @return DataSource or null if not configured
     */
    public DataSource getDataSource(String name) {
        return dataSources.get(name);
    }

    /**
     * Get configuration for a named data source
     *
     * @param name Data source name
     * @return Configuration or null if not configured
     */
    public DataSourceConfig getConfig(String name) {
        return configs.get(name);
    }

    /**
     * Test connection for a named data source
     *
     * @param name Data source name
     * @return Test result
     */
    public ConnectionTestResult testConnection(String name) {
        DataSource dataSource = dataSources.get(name);
        if (dataSource == null) {
            return ConnectionTestResult.failure("Data source not configured: " + name);
        }

        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(5);
            if (valid) {
                String catalog = conn.getCatalog();
                return ConnectionTestResult.success(name, catalog);
            } else {
                return ConnectionTestResult.failure("Connection validation failed");
            }
        } catch (SQLException e) {
            log.error("Failed to test connection for data source: {}", name, e);
            return ConnectionTestResult.failure(e.getMessage());
        }
    }

    /**
     * Test connection with configuration (without storing)
     *
     * @param config Data source configuration
     * @return Test result
     */
    public ConnectionTestResult testConnection(DataSourceConfig config) {
        try {
            String url = buildJdbcUrl(config);
            DataSource dataSource = dataSourceFactory.create(url, config.getUsername(), config.getPassword());

            try (Connection conn = dataSource.getConnection()) {
                boolean valid = conn.isValid(5);
                if (valid) {
                    return ConnectionTestResult.success("(test)", config.getDatabase());
                } else {
                    return ConnectionTestResult.failure("Connection validation failed");
                }
            }
        } catch (Exception e) {
            log.error("Failed to test connection", e);
            return ConnectionTestResult.failure(e.getMessage());
        }
    }

    /**
     * Check if a data source is configured
     *
     * @param name Data source name
     * @return true if configured
     */
    public boolean isConfigured(String name) {
        return dataSources.containsKey(name);
    }

    /**
     * Remove a data source
     *
     * @param name Data source name
     * @return true if removed
     */
    public boolean remove(String name) {
        DataSource removed = dataSources.remove(name);
        configs.remove(name);
        persistence.delete(name);
        return removed != null;
    }

    /**
     * Get all configured data source names
     *
     * @return Set of data source names
     */
    public java.util.Set<String> getConfiguredNames() {
        return new java.util.HashSet<>(dataSources.keySet());
    }

    /**
     * Build JDBC URL from configuration
     */
    private String buildJdbcUrl(DataSourceConfig config) {
        String driver = config.getDriver();

        // Auto-detect driver if not specified
        if (driver == null || driver.isBlank()) {
            driver = "postgresql"; // Default for Odoo
        }

        return switch (driver.toLowerCase()) {
            case "postgresql", "postgres" ->
                    String.format("jdbc:postgresql://%s:%d/%s",
                            config.getHost(), config.getPort(), config.getDatabase());
            case "mysql" ->
                    String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8",
                            config.getHost(), config.getPort(), config.getDatabase());
            case "sqlserver" ->
                    String.format("jdbc:sqlserver://%s:%d;databaseName=%s",
                            config.getHost(), config.getPort(), config.getDatabase());
            case "sqlite" ->
                    String.format("jdbc:sqlite:%s", config.getDatabase());
            default ->
                    throw new IllegalArgumentException("Unsupported driver: " + driver);
        };
    }

    /**
     * Data source configuration
     */
    @Data
    public static class DataSourceConfig {
        private String host;
        private Integer port = 5432;
        private String database;
        private String username;
        private String password;
        private String driver;

        public static DataSourceConfigBuilder builder() {
            return new DataSourceConfigBuilder();
        }

        public static class DataSourceConfigBuilder {
            private String host;
            private Integer port = 5432;
            private String database;
            private String username;
            private String password;
            private String driver;

            public DataSourceConfigBuilder host(String host) {
                this.host = host;
                return this;
            }

            public DataSourceConfigBuilder port(Integer port) {
                this.port = port;
                return this;
            }

            public DataSourceConfigBuilder database(String database) {
                this.database = database;
                return this;
            }

            public DataSourceConfigBuilder username(String username) {
                this.username = username;
                return this;
            }

            public DataSourceConfigBuilder password(String password) {
                this.password = password;
                return this;
            }

            public DataSourceConfigBuilder driver(String driver) {
                this.driver = driver;
                return this;
            }

            public DataSourceConfig build() {
                DataSourceConfig config = new DataSourceConfig();
                config.setHost(host);
                config.setPort(port);
                config.setDatabase(database);
                config.setUsername(username);
                config.setPassword(password);
                config.setDriver(driver);
                return config;
            }
        }
    }

    /**
     * Connection test result
     */
    @Data
    public static class ConnectionTestResult {
        private boolean success;
        private String message;
        private String dataSourceName;
        private String database;

        public static ConnectionTestResult success(String name, String database) {
            ConnectionTestResult result = new ConnectionTestResult();
            result.setSuccess(true);
            result.setMessage("Connection successful");
            result.setDataSourceName(name);
            result.setDatabase(database);
            return result;
        }

        public static ConnectionTestResult failure(String message) {
            ConnectionTestResult result = new ConnectionTestResult();
            result.setSuccess(false);
            result.setMessage(message);
            return result;
        }
    }
}