package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.dataset.db.dialect.DbType;
import com.foggyframework.dataset.db.dialect.FDialect;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Dialect gates for restricted CALCULATE lowering.
 */
public final class CalculateDialectCapabilities {

    private CalculateDialectCapabilities() {
    }

    public static boolean supportsGroupedAggregateWindow(FDialect dialect, DataSource dataSource) {
        if (dialect == null) {
            return true;
        }
        if (dialect.getDbType() != DbType.MYSQL) {
            return true;
        }
        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String productName = metadata.getDatabaseProductName();
            if (productName == null || !productName.toLowerCase().contains("mysql")) {
                return false;
            }
            return metadata.getDatabaseMajorVersion() >= 8;
        } catch (Exception ignored) {
            return false;
        }
    }
}
