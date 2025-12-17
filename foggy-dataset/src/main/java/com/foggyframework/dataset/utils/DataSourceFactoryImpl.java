package com.foggyframework.dataset.utils;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据源工厂实现
 *
 * <p>基于 HikariCP 创建数据源，支持连接池复用和自动驱动检测。
 *
 * <h3>特性：</h3>
 * <ul>
 *   <li>自动检测 JDBC 驱动</li>
 *   <li>连接池复用（相同配置返回相同实例）</li>
 *   <li>支持环境变量读取</li>
 *   <li>合理的默认连接池配置</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
@Slf4j
public class DataSourceFactoryImpl implements DataSourceFactory {

    /**
     * 数据源缓存（相同 URL 复用同一个连接池）
     */
    private final ConcurrentHashMap<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();

    /**
     * 数据源计数器（用于生成唯一的连接池名称）
     */
    private final AtomicInteger poolCounter = new AtomicInteger(0);

    @Override
    public DataSource create(Map<String, Object> config) {
        String url = getRequiredString(config, "url");
        String username = getRequiredString(config, "username");
        String password = getString(config, "password", "");

        // 生成缓存 key
        String cacheKey = generateCacheKey(url, username);

        // 尝试从缓存获取
        return dataSourceCache.computeIfAbsent(cacheKey, k -> {
            log.info("创建新数据源: url={}, username={}", maskUrl(url), username);
            return createHikariDataSource(url, username, password, config);
        });
    }

    @Override
    public DataSource create(String url, String username, String password) {
        RX.hasText(url, "url 不能为空");
        RX.hasText(username, "username 不能为空");

        String cacheKey = generateCacheKey(url, username);

        return dataSourceCache.computeIfAbsent(cacheKey, k -> {
            log.info("创建新数据源: url={}, username={}", maskUrl(url), username);
            return createHikariDataSource(url, username, password, null);
        });
    }

    @Override
    public String env(String name, String defaultValue) {
        String value = System.getenv(name);
        if (StringUtils.isEmpty(value)) {
            // 尝试 System Property（支持 -D 参数）
            value = System.getProperty(name);
        }
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    @Override
    public String env(String name) {
        return env(name, null);
    }

    /**
     * 创建 HikariCP 数据源
     */
    private DataSource createHikariDataSource(String url, String username, String password, Map<String, Object> config) {
        HikariConfig hikariConfig = new HikariConfig();

        // 基本配置
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);

        // 驱动类名（自动检测或手动指定）
        String driverClassName = config != null ? getString(config, "driverClassName", null) : null;
        if (StringUtils.isEmpty(driverClassName)) {
            driverClassName = detectDriver(url);
        }
        if (StringUtils.isNotEmpty(driverClassName)) {
            hikariConfig.setDriverClassName(driverClassName);
        }

        // 连接池名称
        String poolName = config != null ? getString(config, "poolName", null) : null;
        if (StringUtils.isEmpty(poolName)) {
            poolName = "FoggyExternalPool-" + poolCounter.incrementAndGet();
        }
        hikariConfig.setPoolName(poolName);

        // 连接池大小
        hikariConfig.setMaximumPoolSize(getInt(config, "maximumPoolSize", 10));
        hikariConfig.setMinimumIdle(getInt(config, "minimumIdle", 2));

        // 超时配置
        hikariConfig.setConnectionTimeout(getLong(config, "connectionTimeout", 30000L));
        hikariConfig.setIdleTimeout(getLong(config, "idleTimeout", 600000L));
        hikariConfig.setMaxLifetime(getLong(config, "maxLifetime", 1800000L));

        // 验证查询（根据数据库类型）
        String validationQuery = detectValidationQuery(url);
        if (validationQuery != null) {
            hikariConfig.setConnectionTestQuery(validationQuery);
        }

        return new HikariDataSource(hikariConfig);
    }

    /**
     * 根据 JDBC URL 自动检测驱动类名
     */
    private String detectDriver(String url) {
        if (url == null) {
            return null;
        }

        String lowerUrl = url.toLowerCase();

        if (lowerUrl.contains(":mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        } else if (lowerUrl.contains(":postgresql:")) {
            return "org.postgresql.Driver";
        } else if (lowerUrl.contains(":sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        } else if (lowerUrl.contains(":sqlite:")) {
            return "org.sqlite.JDBC";
        } else if (lowerUrl.contains(":oracle:")) {
            return "oracle.jdbc.OracleDriver";
        } else if (lowerUrl.contains(":h2:")) {
            return "org.h2.Driver";
        } else if (lowerUrl.contains(":mariadb:")) {
            return "org.mariadb.jdbc.Driver";
        }

        log.warn("无法自动检测 JDBC 驱动，请手动指定 driverClassName: {}", maskUrl(url));
        return null;
    }

    /**
     * 根据 JDBC URL 检测验证查询语句
     */
    private String detectValidationQuery(String url) {
        if (url == null) {
            return null;
        }

        String lowerUrl = url.toLowerCase();

        if (lowerUrl.contains(":oracle:")) {
            return "SELECT 1 FROM DUAL";
        } else if (lowerUrl.contains(":sqlserver:")) {
            return "SELECT 1";
        } else {
            // MySQL, PostgreSQL, SQLite, H2 等都支持
            return "SELECT 1";
        }
    }

    /**
     * 生成缓存 key
     */
    private String generateCacheKey(String url, String username) {
        return url + "|" + username;
    }

    /**
     * 脱敏 URL（隐藏密码等敏感信息）
     */
    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        // 简单脱敏：隐藏密码参数
        return url.replaceAll("password=[^&]*", "password=***");
    }

    // ==================== 配置读取辅助方法 ====================

    private String getRequiredString(Map<String, Object> config, String key) {
        String value = getString(config, key, null);
        RX.hasText(value, key + " 不能为空");
        return value;
    }

    private String getString(Map<String, Object> config, String key, String defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private int getInt(Map<String, Object> config, String key, int defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long getLong(Map<String, Object> config, String key, long defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
