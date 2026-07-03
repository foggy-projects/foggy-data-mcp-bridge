package com.foggyframework.runtime.api.service;

import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.RuntimeDatasourceRecord;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

@Component
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class HikariManagedDataSourcePoolFactory implements ManagedDataSourcePoolFactory {

    @Override
    public ManagedDataSourcePool create(
            RuntimeDatasourceRecord record,
            String password,
            ManagedDataSourcePoolSettings settings
    ) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName(record.name()));
        config.setJdbcUrl(record.jdbcUrl());
        if (StringUtils.hasText(settings.driverClassName())) {
            config.setDriverClassName(settings.driverClassName());
        }
        if (StringUtils.hasText(record.username())) {
            config.setUsername(record.username());
        }
        if (password != null) {
            config.setPassword(password);
        }
        config.setMaximumPoolSize(settings.maximumPoolSize());
        config.setMinimumIdle(settings.minimumIdle());
        config.setConnectionTimeout(settings.connectionTimeoutMs());
        config.setIdleTimeout(settings.idleTimeoutMs());
        config.setMaxLifetime(settings.maxLifetimeMs());
        return new HikariManagedDataSourcePool(new HikariDataSource(config));
    }

    private String poolName(String name) {
        String normalized = StringUtils.hasText(name) ? name.replaceAll("[^A-Za-z0-9_.-]", "-") : "datasource";
        return "foggy-runtime-" + normalized;
    }

    private static final class HikariManagedDataSourcePool implements ManagedDataSourcePool {

        private final HikariDataSource delegate;

        private HikariManagedDataSourcePool(HikariDataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return delegate.getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public int activeConnections() {
            HikariPoolMXBean mxBean = delegate.getHikariPoolMXBean();
            return mxBean != null ? mxBean.getActiveConnections() : -1;
        }
    }
}
