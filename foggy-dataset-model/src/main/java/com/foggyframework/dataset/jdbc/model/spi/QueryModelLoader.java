package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.BundleResource;

public interface QueryModelLoader {
    void clearAll();

    QueryModel getJdbcQueryModel(String queryModelName);

    QueryModel loadJdbcQueryModel(BundleResource bundleResource);
}
