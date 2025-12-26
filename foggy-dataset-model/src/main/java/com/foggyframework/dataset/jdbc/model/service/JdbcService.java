package com.foggyframework.dataset.jdbc.model.service;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.jdbc.model.common.result.DbDataItem;
import com.foggyframework.dataset.jdbc.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.PagingResultImpl;

public interface JdbcService {
    PagingResultImpl<DbDataItem> queryDimensionData(PagingRequest<DimensionDataQueryForm> form);

    PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form);

    DbQueryResult queryModelResult(PagingRequest<DbQueryRequestDef> form);

}
