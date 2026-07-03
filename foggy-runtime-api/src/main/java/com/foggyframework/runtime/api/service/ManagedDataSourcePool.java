package com.foggyframework.runtime.api.service;

import javax.sql.DataSource;

public interface ManagedDataSourcePool extends DataSource, AutoCloseable {

    @Override
    void close();

    boolean isClosed();

    int activeConnections();
}
