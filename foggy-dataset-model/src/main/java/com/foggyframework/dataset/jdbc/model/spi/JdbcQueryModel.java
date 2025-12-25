package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.jdbc.model.common.result.KpiResultImpl;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQueryResult;
import com.foggyframework.dataset.jdbc.model.impl.query.JdbcQueryOrderColumnImpl;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.jdbc.model.spi.support.JdbcColumnGroup;

import jakarta.annotation.Nullable;
import javax.sql.DataSource;
import java.util.List;

public interface JdbcQueryModel extends QueryModel{
    FDialect getDialect();


//    default String getAlias(JdbcColumn jdbcColumn) {
//        return getAlias(jdbcColumn.getQueryObject());
//    }
//    default String getAlias(JdbcDimension dimension) {
//        return getAlias(dimension.getQueryObject());
//    }

}
