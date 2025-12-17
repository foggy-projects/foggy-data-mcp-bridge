package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.BundleResource;

public interface JdbcQueryModelLoader {
    void clearAll();

    JdbcQueryModel getJdbcQueryModel(String queryModelName);

    JdbcQueryModel loadJdbcQueryModel(BundleResource bundleResource);
}
