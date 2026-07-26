package com.foggyframework.dataset.model.service;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.model.common.result.DbDataItem;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.PagingResultImpl;

public interface JdbcService {
    PagingResultImpl<DbDataItem> queryDimensionData(PagingRequest<DimensionDataQueryForm> form);

    default PagingResultImpl<DbDataItem> queryDimensionData(
            PagingRequest<DimensionDataQueryForm> form,
            String authorization,
            String namespace
    ) {
        return queryDimensionData(form);
    }

    PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form);

    DbQueryResult queryModelResult(PagingRequest<DbQueryRequestDef> form);

}
