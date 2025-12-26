package com.foggyframework.dataset.jdbc.model.service.impl;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.jdbc.model.common.result.JdbcDataItem;
import com.foggyframework.dataset.jdbc.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQueryResult;
import com.foggyframework.dataset.jdbc.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.jdbc.model.service.JdbcService;
import com.foggyframework.dataset.jdbc.model.spi.DbQueryDimension;
import com.foggyframework.dataset.jdbc.model.spi.QueryModelLoader;
import com.foggyframework.dataset.jdbc.model.spi.QueryModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.semantic.impl.facade.SemanticFacade;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * JDBC 查询服务实现
 * <p>
 * 纯查询服务，不包含 Step 处理逻辑。
 * 如需完整的查询生命周期（beforeQuery -> query -> process），
 * 请使用 {@link QueryFacade}。
 * </p>
 *
 * @see JdbcServiceImpl
 */
public class JdbcServiceImpl implements JdbcService {

    @Resource
    QueryModelLoader queryModelLoader;
    @Resource
    SystemBundlesContext systemBundlesContext;

    @Resource
    SemanticFacade semanticFacade;

    @Override
    public PagingResultImpl<JdbcDataItem> queryDimensionData(PagingRequest<DimensionDataQueryForm> form) {
        DimensionDataQueryForm qf = form.getParam();
        String queryModelName = qf.getQueryModel();
        String dimensionName = qf.getDimension();
        QueryModel jdbcQueryModel = queryModelLoader.getJdbcQueryModel(queryModelName);

        RX.notNull(jdbcQueryModel, "未能找到查询模型:" + dimensionName);

        DbQueryDimension jdbcDimension = jdbcQueryModel.findQueryDimension(dimensionName, true);
        JdbcQueryModelImpl jdbcQueryModelImpl = jdbcQueryModel.getDecorate(JdbcQueryModelImpl.class);
        if(jdbcQueryModelImpl == null){
            throw new RuntimeException("目前只有jdbc模型才支持维度数据查询" );
        }
        List<JdbcDataItem> ll = jdbcDimension.queryDimensionDataByHierarchy(systemBundlesContext, jdbcQueryModelImpl.getDataSource(), jdbcDimension, qf.getHierarchy());

        return PagingResultImpl.of(ll,ll.size());
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form) {
        DbQueryRequestDef qf = form.getParam();
        String queryModelName = qf.getQueryModel();
        QueryModel jdbcQueryModel = queryModelLoader.getJdbcQueryModel(queryModelName);

        PagingResultImpl p = jdbcQueryModel.query(systemBundlesContext, form).getPagingResult();
        return p;
    }

    @Override
    public JdbcQueryResult queryModelResult(PagingRequest<DbQueryRequestDef> form) {
        DbQueryRequestDef qf = form.getParam();
        String queryModelName = qf.getQueryModel();
        QueryModel jdbcQueryModel = queryModelLoader.getJdbcQueryModel(queryModelName);

        JdbcQueryResult p = jdbcQueryModel.query(systemBundlesContext, form);
        return p;
    }

}
