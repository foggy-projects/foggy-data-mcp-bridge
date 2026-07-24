package com.foggyframework.dataset.model.lifecycle.port;

import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;

import javax.sql.DataSource;

/** A bounded in-flight right to use one exact datasource generation. */
public interface BindingLease extends AutoCloseable {

    DatasourceBindingIdentity identity();

    DataSource dataSource();

    BindingAdmissionState state();

    @Override
    void close();
}
