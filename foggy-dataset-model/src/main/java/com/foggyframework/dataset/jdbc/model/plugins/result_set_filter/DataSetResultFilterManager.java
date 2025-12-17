package com.foggyframework.dataset.jdbc.model.plugins.result_set_filter;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.model.PagingResultImpl;

public interface DataSetResultFilterManager {

    PagingResultImpl process(PagingRequest<JdbcQueryRequestDef> form, PagingResultImpl pagingResult);

    PagingRequest<JdbcQueryRequestDef> beforeQuery(PagingRequest<JdbcQueryRequestDef> form);

    /**
     * 得到查询结果PagingResultImpl后,放入context,调用该方法处理
     * @param context
     */
    void process(ModelResultContext context);

    void beforeQuery(ModelResultContext context);
}
