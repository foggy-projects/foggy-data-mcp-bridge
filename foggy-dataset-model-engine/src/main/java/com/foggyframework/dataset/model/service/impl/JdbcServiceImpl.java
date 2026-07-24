package com.foggyframework.dataset.model.service.impl;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.model.common.result.DbDataItem;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelDescriptor;
import com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelResolver;
import com.foggyframework.dataset.model.service.JdbcService;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.DbQueryDimension;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JDBC 查询服务实现
 * <p>
 * 纯查询服务，不包含 Step 处理逻辑。
 * 如需完整的查询生命周期（beforeQuery -> query -> process），
 * 请使用 {@link AdvancedQueryFacade}。
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
    AdvancedQueryFacade queryFacade;

    private final SyntheticMemberQueryModelResolver syntheticMemberQueryModelResolver = new SyntheticMemberQueryModelResolver();

    @Override
    public PagingResultImpl<DbDataItem> queryDimensionData(PagingRequest<DimensionDataQueryForm> form) {
        DimensionDataQueryForm qf = form.getParam();
        SyntheticMemberQueryModelDescriptor descriptor = syntheticMemberQueryModelResolver.resolve(
                queryModelLoader,
                qf.getQueryModel(),
                qf.getDimension(),
                null
        );

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(descriptor.syntheticModelName());
        queryRequest.setColumns(List.of("id", "caption"));
        queryRequest.setReturnTotal(true);
        queryRequest.setExtData(qf.getExtData());
        SliceRequestDef hierarchySlice = resolveHierarchySlice(qf.getHierarchy());
        if (hierarchySlice != null) {
            queryRequest.setSlice(List.of(hierarchySlice));
        }

        PagingRequest<DbQueryRequestDef> memberRequest = form.copy(queryRequest);
        PagingResultImpl rawResult = queryFacade.queryModelData(memberRequest);

        List<DbDataItem> dataItems = new ArrayList<>();
        if (rawResult.getItems() != null) {
            for (Object item : rawResult.getItems()) {
                if (item instanceof Map<?, ?> row) {
                    dataItems.add(new DbDataItem(row.get("id"), row.get("caption") == null ? null : String.valueOf(row.get("caption"))));
                }
            }
        }

        return PagingResultImpl.of(
                dataItems,
                memberRequest.getStart(),
                memberRequest.getLimit(),
                rawResult.getTotalData(),
                Math.toIntExact(rawResult.getTotal())
        );
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form) {
        DbQueryRequestDef qf = form.getParam();
        String queryModelName = qf.getQueryModel();
        QueryModel jdbcQueryModel = queryModelLoader.getJdbcQueryModel(queryModelName, null);

        PagingResultImpl p = jdbcQueryModel.query(systemBundlesContext, form).getPagingResult();
        return p;
    }

    @Override
    public DbQueryResult queryModelResult(PagingRequest<DbQueryRequestDef> form) {
        DbQueryRequestDef qf = form.getParam();
        String queryModelName = qf.getQueryModel();
        QueryModel jdbcQueryModel = queryModelLoader.getJdbcQueryModel(queryModelName, null);

        DbQueryResult p = jdbcQueryModel.query(systemBundlesContext, form);
        return p;
    }

    /**
     * Converts the simple-entry hierarchy string into the canonical slice used by synthetic member-QM.
     * Supported forms include plain member id, {@code op:value}, and {@code op(value[,maxDepth])}.
     * When no operator is provided, it defaults to {@code selfAndDescendantsOf} and always targets the
     * synthetic model's canonical {@code id} field.
     */
    private SliceRequestDef resolveHierarchySlice(String hierarchy) {
        if (StringUtils.isEmpty(hierarchy)) {
            return null;
        }

        String trimmed = hierarchy.trim();
        String op = "selfAndDescendantsOf";
        String value = trimmed;
        Integer maxDepth = null;

        int colonIndex = trimmed.indexOf(':');
        if (colonIndex > 0) {
            op = trimmed.substring(0, colonIndex).trim();
            value = trimmed.substring(colonIndex + 1).trim();
        } else if (trimmed.endsWith(")") && trimmed.contains("(")) {
            int openIndex = trimmed.indexOf('(');
            op = trimmed.substring(0, openIndex).trim();
            String args = trimmed.substring(openIndex + 1, trimmed.length() - 1).trim();
            int commaIndex = args.indexOf(',');
            if (commaIndex >= 0) {
                value = args.substring(0, commaIndex).trim();
                String depthArg = args.substring(commaIndex + 1).trim();
                if (StringUtils.isNotEmpty(depthArg)) {
                    maxDepth = Integer.valueOf(depthArg);
                }
            } else {
                value = args;
            }
        }

        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("id");
        slice.setOp(op);
        slice.setValue(value);
        slice.setMaxDepth(maxDepth);
        return slice;
    }

}
