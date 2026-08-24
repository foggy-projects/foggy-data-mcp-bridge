package com.foggyframework.analytics.console.catalog;

import com.foggyframework.analytics.console.model.AnalyticsConsoleCatalogState;

import java.util.function.UnaryOperator;

public interface AnalyticsConsoleCatalogRepository {

    AnalyticsConsoleCatalogState read();

    AnalyticsConsoleCatalogState update(UnaryOperator<AnalyticsConsoleCatalogState> change);
}
