package com.foggyframework.dataset.db.model.engine.pivot.sql;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 构建用于 {@code QueryFacade.prepareManagedRelation()} 的 ModelResultContext。
 *
 * <p>从 PivotPipeline 中提取，消除 Pipeline 中 60+ 行的对象构造代码。
 * 将语义层请求转换为 JDBC 层请求定义，并透传安全上下文
 * （namespace、securityContext、fieldAccess、deniedColumns、systemSlice）。</p>
 */
public final class ManagedRelationContextBuilder {

    private ManagedRelationContextBuilder() {
    }

    /**
     * 构建 ModelResultContext
     *
     * @param model       模型名称
     * @param flatRequest Phase 1 扁平查询请求
     * @param reqContext  语义请求上下文（含安全信息）
     * @return 可用于 prepareManagedRelation 的 ModelResultContext
     */
    public static ModelResultContext build(
            String model,
            SemanticQueryRequest flatRequest,
            SemanticRequestContext reqContext) {

        // 构建 JDBC 请求
        DbQueryRequestDef queryDef = new DbQueryRequestDef();
        queryDef.setQueryModel(model);
        queryDef.setReturnTotal(false);
        queryDef.setStrictColumns(true);
        queryDef.setColumns(flatRequest.getColumns());

        // groupBy
        if (flatRequest.getGroupBy() != null) {
            List<GroupRequestDef> jdbcGroupBy = new ArrayList<>();
            for (SemanticQueryRequest.GroupByItem item : flatRequest.getGroupBy()) {
                GroupRequestDef g = new GroupRequestDef();
                g.setField(item.getField());
                g.setAgg(item.getAgg());
                jdbcGroupBy.add(g);
            }
            queryDef.setGroupBy(jdbcGroupBy);
        }

        // slice
        if (flatRequest.getSlice() != null) {
            List<SliceRequestDef> jdbcSlice = new ArrayList<>();
            for (SemanticQueryRequest.SliceItem sliceItem : flatRequest.getSlice()) {
                SliceRequestDef s = new SliceRequestDef();
                s.setField(sliceItem.getField());
                s.setOp(sliceItem.getOp());
                s.setValue(sliceItem.getValue());
                jdbcSlice.add(s);
            }
            queryDef.setSlice(jdbcSlice);
        }

        // calculatedFields
        if (flatRequest.getCalculatedFields() != null) {
            queryDef.setCalculatedFields(new ArrayList<>(flatRequest.getCalculatedFields()));
        }

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryDef);
        pagingRequest.setStart(0);
        pagingRequest.setPageSize(flatRequest.getLimit());

        ModelResultContext resultContext = new ModelResultContext();
        resultContext.setRequest(pagingRequest);
        resultContext.setQueryType(ModelResultContext.QueryType.SEMANTIC);
        resultContext.setNamespace(reqContext.getNamespace());
        resultContext.setSecurityContext(reqContext.getSecurityContext());
        resultContext.setFieldAccess(reqContext.getFieldAccess());
        resultContext.setDeniedColumns(reqContext.getDeniedColumns());
        resultContext.setSystemSlice(reqContext.getSystemSlice());

        return resultContext;
    }
}
