package com.foggyframework.dataset.db.dialect;

/**
 * MySQL 8+ dialect capability profile.
 *
 * <p>The SQL syntax is still rendered by {@link MysqlDialect}; this subclass
 * only opens features that are absent in the conservative MySQL 5.7 profile.</p>
 */
public class Mysql8Dialect extends MysqlDialect {

    @Override
    public boolean supportsCte() {
        return true;
    }

    @Override
    public boolean supportsWindowFunctions() {
        return true;
    }
}
