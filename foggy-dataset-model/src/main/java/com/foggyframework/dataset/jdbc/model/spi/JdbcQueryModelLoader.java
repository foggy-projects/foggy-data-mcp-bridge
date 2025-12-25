package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.BundleResource;

public interface JdbcQueryModelLoader {
    void clearAll();

    QueryModel getJdbcQueryModel(String queryModelName);

    QueryModel loadJdbcQueryModel(BundleResource bundleResource);
}
