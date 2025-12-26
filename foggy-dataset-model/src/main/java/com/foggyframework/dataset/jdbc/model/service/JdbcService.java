package com.foggyframework.dataset.jdbc.model.service;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.jdbc.model.common.result.JdbcDataItem;
import com.foggyframework.dataset.jdbc.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQueryResult;
import com.foggyframework.dataset.model.PagingResultImpl;

public interface JdbcService {
    PagingResultImpl<JdbcDataItem> queryDimensionData(PagingRequest<DimensionDataQueryForm> form);

    PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form);

    JdbcQueryResult queryModelResult(PagingRequest<DbQueryRequestDef> form);

}
