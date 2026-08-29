package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePlan;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePreparation;
import com.foggyframework.dataset.model.impl.measure.DbMeasureSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbMeasure;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.TableModel;
import jakarta.annotation.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AverageTotalDataTestSupport extends EcommerceTestSupport {
    protected static final BigDecimal EPSILON = new BigDecimal("0.000001");

    @Resource
    protected AdvancedQueryFacade queryFacade;

    protected DbQueryRequestDef baseRequest() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setReturnTotal(true);
        return request;
    }

    protected DbQueryRequestDef groupedRequest(String... fields) {
        DbQueryRequestDef request = baseRequest();
        request.setGroupBy(java.util.Arrays.stream(fields).map(this::group).toList());
        return request;
    }

    protected GroupRequestDef group(String field) {
        GroupRequestDef group = new GroupRequestDef();
        group.setField(field);
        return group;
    }

    protected OrderRequestDef order(String field, String direction) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir(direction);
        return order;
    }

    protected CalculatedFieldDef calculated(
            String name, String expression, String aggregation) {
        CalculatedFieldDef field = new CalculatedFieldDef(name, name, expression);
        field.setAgg(aggregation);
        return field;
    }

    protected DbQueryResult queryWithAverageMeasure(
            DbQueryRequestDef request,
            int start,
            int limit,
            String measureName) {
        return queryWithMeasureAggregation(
                request, start, limit, measureName, DbAggregation.AVG);
    }

    protected DbQueryResult queryWithAverageMeasureAndDomain(
            DbQueryRequestDef request,
            int start,
            int limit,
            String measureName,
            DomainTransportPlan domain) {
        DbMeasureSupport measure = findMeasure(measureName);
        DbAggregation original = measure.getAggregation();
        measure.setAggregation(DbAggregation.AVG);
        try {
            PagingRequest<DbQueryRequestDef> paging = new PagingRequest<>(
                    start / Math.max(limit, 1) + 1, limit, start, limit, request);
            ModelResultContext context = new ModelResultContext();
            context.setRequest(paging);
            context.getExtData().put(DomainTransportPlan.EXT_DATA_KEY, List.of(domain));
            return queryFacade.queryModelResult(context);
        } finally {
            measure.setAggregation(original);
        }
    }

    protected DbQueryResult queryWithMeasureAggregation(
            DbQueryRequestDef request,
            int start,
            int limit,
            String measureName,
            DbAggregation aggregation) {
        DbMeasureSupport measure = findMeasure(measureName);
        DbAggregation original = measure.getAggregation();
        measure.setAggregation(aggregation);
        try {
            PagingRequest<DbQueryRequestDef> paging = new PagingRequest<>(
                    start / Math.max(limit, 1) + 1, limit, start, limit, request);
            return queryFacade.queryModelResult(paging);
        } finally {
            measure.setAggregation(original);
        }
    }

    protected Object predefinedAggregateTotal(
            String measureName, DbAggregation aggregation) {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of("product$categoryName", measureName));
        PagingResultImpl<?> result = queryWithMeasureAggregation(
                request, 0, 100, measureName, aggregation).getPagingResult();
        return totalData(result).get(measureName);
    }

    protected DbMeasureSupport findMeasure(String measureName) {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        for (TableModel tableModel : queryModel.getJdbcModelList()) {
            DbMeasure measure = tableModel.findJdbcMeasureByName(measureName);
            if (measure instanceof DbMeasureSupport support) {
                return support;
            }
        }
        throw new AssertionError("测试模型中未找到 measure: " + measureName);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> totalData(PagingResultImpl<?> result) {
        assertNotNull(result.getTotalData(), "returnTotal=true 时必须返回 totalData");
        assertTrue(result.getTotalData() instanceof Map, "totalData 必须是 Map");
        return (Map<String, Object>) result.getTotalData();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> row(Object item) {
        return (Map<String, Object>) item;
    }

    protected BigDecimal nativeDecimal(String sql) {
        return decimal(jdbcTemplate.queryForObject(sql, Object.class));
    }

    protected BigDecimal nativeDecimal(String sql, Object... args) {
        return decimal(jdbcTemplate.queryForObject(sql, Object.class, args));
    }

    protected BigDecimal decimal(Object value) {
        assertNotNull(value, "数值不应为 null");
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    protected void assertSharedResultStageTotalSql(
            DbQueryResult queryResult,
            String expectedResultStageAlias) {
        JdbcModelQueryEngine engine = (JdbcModelQueryEngine) queryResult.getQueryEngine();
        String totalSql = engine.getAggSql();
        assertTrue(totalSql.contains("WITH stage1 AS"), totalSql);
        assertTrue(totalSql.contains(expectedResultStageAlias + " AS"), totalSql);
        assertFalse(totalSql.contains("__foggy_total_stage_"), totalSql);
        assertFalse(totalSql.contains("WITH __foggy_total_base AS"), totalSql);
    }

    protected void assertPreparedHiddenDependencySharedByMainAndTotal(
            DbQueryResult queryResult,
            String alias) {
        JdbcModelQueryEngine engine = (JdbcModelQueryEngine) queryResult.getQueryEngine();
        ResultStagePreparation preparation = (ResultStagePreparation)
                ReflectionTestUtils.getField(engine, "resultStagePreparation");
        assertNotNull(preparation, "window 请求必须在 visitor 前生成 request preparation");
        ResultStagePreparation.Projection main = preparation.baseProjectionPlan().main().projections()
                .stream()
                .filter(projection -> projection.column().role()
                        == ResultStagePlan.ColumnRole.HIDDEN_DEPENDENCY)
                .filter(projection -> alias.equals(projection.column().alias()))
                .findFirst()
                .orElseThrow();
        ResultStagePreparation.Projection total = preparation.baseProjectionPlan().total().projections()
                .stream()
                .filter(projection -> projection.column().role()
                        == ResultStagePlan.ColumnRole.HIDDEN_DEPENDENCY)
                .filter(projection -> alias.equals(projection.column().alias()))
                .findFirst()
                .orElseThrow();
        assertSame(main.source(), total.source(),
                "MAIN/TOTAL hidden dependency 必须来自一次 prepare 的同一个物理列绑定");
    }

    protected void assertDecimalEquals(
            BigDecimal expected, Object actual, String message) {
        BigDecimal actualDecimal = decimal(actual);
        assertTrue(expected.subtract(actualDecimal).abs().compareTo(EPSILON) <= 0,
                () -> message + ", expected=" + expected + ", actual=" + actualDecimal);
    }
}
