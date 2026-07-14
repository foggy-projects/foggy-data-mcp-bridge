package com.foggyframework.dataset.mcp.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Source Configuration Persistence
 *
 * <p>Persists data source configurations to JSON files.
 * Configuration directory: ${foggy.datasource.config.dir} or ~/.foggy/datasources/
 *
 * <p>File format: {name}.json
 * <pre>
 * {
 *   "name": "odoo",
 *   "host": "localhost",
 *   "port": 5432,
 *   "database": "odoo",
 *   "username": "odoo",
 *   "password": "password",
 *   "driver": "postgresql"
 * }
 * </pre>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@Component
public class DataSourceConfigPersistence {

    private final ObjectMapper objectMapper;

    @Value("${foggy.datasource.config.dir:#{T(java.lang.System).getProperty('user.home') + '/.foggy/datasources'}}")
    private String configDir;

    public DataSourceConfigPersistence() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() {
        // Ensure directory exists
        Path path = Paths.get(configDir);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                log.info("Created data source config directory: {}", path.toAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to create config directory: {}", path, e);
            }
        }
    }

    /**
     * Save a data source configuration
     *
     * @param name   Data source name
     * @param config Configuration to save
     */
    public void save(String name, DataSourceManager.DataSourceConfig config) {
        File file = getConfigFile(name);
        try {
            ConfigWrapper wrapper = new ConfigWrapper();
            wrapper.setName(name);
            wrapper.setHost(config.getHost());
            wrapper.setPort(config.getPort());
            wrapper.setDatabase(config.getDatabase());
            wrapper.setUsername(config.getUsername());
            wrapper.setPassword(config.getPassword());
            wrapper.setDriver(config.getDriver());

            objectMapper.writeValue(file, wrapper);
            log.info("Saved data source config: {} -> {}", name, file.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save data source config: {}, reason={}",
                    name, e.getClass().getSimpleName());
            throw new RuntimeException("Failed to save data source config: " + name, e);
        }
    }

    /**
     * Load a data source configuration
     *
     * @param name Data source name
     * @return Configuration or null if not found
     */
    public DataSourceManager.DataSourceConfig load(String name) {
        File file = getConfigFile(name);
        if (!file.exists()) {
            return null;
        }

        try {
            ConfigWrapper wrapper = objectMapper.readValue(file, ConfigWrapper.class);
            DataSourceManager.DataSourceConfig config = new DataSourceManager.DataSourceConfig();
            config.setHost(wrapper.getHost());
            config.setPort(wrapper.getPort());
            config.setDatabase(wrapper.getDatabase());
            config.setUsername(wrapper.getUsername());
            config.setPassword(wrapper.getPassword());
            config.setDriver(wrapper.getDriver());
            return config;
        } catch (IOException e) {
            log.error("Failed to load data source config: {}, reason={}",
                    name, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Load all saved configurations
     *
     * @return Map of name -> config
     */
    public Map<String, DataSourceManager.DataSourceConfig> loadAll() {
        Map<String, DataSourceManager.DataSourceConfig> result = new HashMap<>();

        File dir = new File(configDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return result;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return result;
        }

        for (File file : files) {
            try {
                ConfigWrapper wrapper = objectMapper.readValue(file, ConfigWrapper.class);
                DataSourceManager.DataSourceConfig config = new DataSourceManager.DataSourceConfig();
                config.setHost(wrapper.getHost());
                config.setPort(wrapper.getPort());
                config.setDatabase(wrapper.getDatabase());
                config.setUsername(wrapper.getUsername());
                config.setPassword(wrapper.getPassword());
                config.setDriver(wrapper.getDriver());
                result.put(wrapper.getName(), config);
                log.info("Loaded data source config: {}", wrapper.getName());
            } catch (IOException e) {
                log.warn("Failed to load config file: {}, reason={}",
                        file.getName(), e.getClass().getSimpleName());
            }
        }

        return result;
    }

    /**
     * Delete a data source configuration
     *
     * @param name Data source name
     * @throws IllegalStateException when an existing file cannot be deleted
     */
    public void delete(String name) {
        File file = getConfigFile(name);
        try {
            boolean deleted = Files.deleteIfExists(file.toPath());
            if (deleted) {
                log.info("Deleted data source config: {}", name);
            }
        } catch (IOException e) {
            log.error("Failed to delete data source config: {}", name);
            throw new IllegalStateException("Failed to delete data source config: " + name, e);
        }
    }

    /**
     * Check if a configuration exists
     *
     * @param name Data source name
     * @return true if exists
     */
    public boolean exists(String name) {
        return getConfigFile(name).exists();
    }

    /**
     * Get the config file for a data source
     */
    private File getConfigFile(String name) {
        return new File(configDir, name + ".json");
    }

    /**
     * Wrapper class for JSON serialization
     */
    public static class ConfigWrapper {
        private String name;
        private String host;
        private Integer port = 5432;
        private String database;
        private String username;
        private String password;
        private String driver;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDriver() { return driver; }
        public void setDriver(String driver) { this.driver = driver; }
    }
}
