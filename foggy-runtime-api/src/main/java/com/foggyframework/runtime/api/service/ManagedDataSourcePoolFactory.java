package com.foggyframework.runtime.api.service;

import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.RuntimeDatasourceRecord;

public interface ManagedDataSourcePoolFactory {

    ManagedDataSourcePool create(
            RuntimeDatasourceRecord record,
            String password,
            ManagedDataSourcePoolSettings settings
    );
}
