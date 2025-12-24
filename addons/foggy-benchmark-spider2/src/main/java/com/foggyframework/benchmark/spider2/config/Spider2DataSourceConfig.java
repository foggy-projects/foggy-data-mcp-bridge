package com.foggyframework.benchmark.spider2.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Spider2 SQLite 数据源配置
 *
 * 功能：
 * 1. 启动时自动扫描并注册所有 SQLite 数据库的 DataSource Bean（用于 JM 模型引用）
 * 2. 提供动态创建和缓存数据源的方法（用于测试代码）
 */
@Slf4j
@Configuration
public class Spider2DataSourceConfig implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private static final Map<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();
    private static final Map<String, JdbcTemplate> jdbcTemplateCache = new ConcurrentHashMap<>();

    private Environment environment;
    private String databaseBasePath;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        // 从 Environment 读取配置，支持通过环境变量 SPIDER2_BASE_PATH 设置基础路径
        String defaultPath = System.getenv().getOrDefault("SPIDER2_BASE_PATH", "./spider2-data") + "/spider2-lite/resource/databases/spider2-localdb";
        this.databaseBasePath = environment.getProperty("spider2.database-base-path", defaultPath);
    }

    // ==================== BeanDefinitionRegistryPostProcessor ====================

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (databaseBasePath == null || databaseBasePath.isEmpty()) {
            log.warn("spider2.database-base-path not configured, skipping DataSource registration");
            return;
        }

        Path dbBasePath = Path.of(databaseBasePath);
        if (!Files.exists(dbBasePath) || !Files.isDirectory(dbBasePath)) {
            log.warn("Spider2 database base path not found: {}, skipping DataSource registration", databaseBasePath);
            return;
        }

        log.info("Registering Spider2 SQLite DataSources from: {}", databaseBasePath);

        try (Stream<Path> paths = Files.list(dbBasePath)) {
            paths.filter(p -> p.toString().endsWith(".sqlite"))
                 .forEach(dbFile -> {
                     String fileName = dbFile.getFileName().toString();
                     String dbName = fileName.replace(".sqlite", "");
                     String beanName = toCamelCase(dbName) + "DataSource";

                     // 注册数据源 Bean
                     GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
                     beanDefinition.setBeanClass(DriverManagerDataSource.class);
                     beanDefinition.setInstanceSupplier(() -> createDataSourceForBean(dbFile));

                     registry.registerBeanDefinition(beanName, beanDefinition);
                     log.info("Registered Spider2 DataSource Bean: {} -> {}", beanName, dbFile.getFileName());
                 });
        } catch (Exception e) {
            log.error("Failed to register Spider2 DataSources: {}", e.getMessage(), e);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // No-op
    }

    /**
     * 为 Bean 注册创建数据源
     */
    private static DataSource createDataSourceForBean(Path dbPath) {
        if (!Files.exists(dbPath)) {
            log.warn("SQLite database not found: {}, using in-memory database", dbPath);
            DriverManagerDataSource emptyDs = new DriverManagerDataSource();
            emptyDs.setDriverClassName("org.sqlite.JDBC");
            emptyDs.setUrl("jdbc:sqlite::memory:");
            return emptyDs;
        }

        String jdbcUrl = "jdbc:sqlite:" + dbPath.toString();
        log.debug("Creating SQLite DataSource: {}", jdbcUrl);

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(jdbcUrl);

        return dataSource;
    }

    // ==================== 动态数据源访问（用于测试代码） ====================

    /**
     * 获取指定数据库的 DataSource（动态创建并缓存）
     */
    public DataSource getDataSource(String databaseName) {
        return dataSourceCache.computeIfAbsent(databaseName, this::createDataSource);
    }

    /**
     * 获取指定数据库的 JdbcTemplate（动态创建并缓存）
     */
    public JdbcTemplate getJdbcTemplate(String databaseName) {
        return jdbcTemplateCache.computeIfAbsent(databaseName, name ->
            new JdbcTemplate(getDataSource(name))
        );
    }

    /**
     * 创建 SQLite DataSource
     */
    private DataSource createDataSource(String databaseName) {
        Path dbPath = getSqlitePath(databaseName);

        if (!Files.exists(dbPath)) {
            throw new IllegalArgumentException("SQLite database not found: " + dbPath);
        }

        String jdbcUrl = "jdbc:sqlite:" + dbPath.toString();
        log.info("Creating SQLite DataSource for database: {} -> {}", databaseName, jdbcUrl);

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(jdbcUrl);

        return dataSource;
    }

    /**
     * 获取 SQLite 文件路径
     */
    public Path getSqlitePath(String databaseName) {
        return Path.of(databaseBasePath, databaseName + ".sqlite");
    }

    /**
     * 检查数据库是否存在
     */
    public boolean isDatabaseAvailable(String databaseName) {
        return Files.exists(getSqlitePath(databaseName));
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        dataSourceCache.clear();
        jdbcTemplateCache.clear();
    }

    // ==================== 工具方法 ====================

    /**
     * 转换为 PascalCase
     * 例如：adventure_works -> AdventureWorks
     */
    private String toPascalCase(String name) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == '_' || c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * 转换为 camelCase
     * 例如：AdventureWorks -> adventureWorks, E_commerce -> eCommerce
     */
    private String toCamelCase(String name) {
        String pascal = toPascalCase(name);
        if (pascal.isEmpty()) return pascal;
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }
}


