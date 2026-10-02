package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ViewerModelMeasureHavingTest extends EcommerceTestSupport {
    @Resource SemanticQueryServiceV3 queries;
    private SemanticQueryRequest request() {
        var request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId","salesAmountYuan"));
        request.setGroupBy(List.of(new SemanticQueryRequest.GroupByItem("orderId",null)));
        return request;
    }
    private SemanticQueryRequest.SliceItem range() {
        var filter = new SemanticQueryRequest.SliceItem();
        filter.setField("salesAmountYuan"); filter.setOp("[]"); filter.setValue(List.of(1,2));
        return filter;
    }
    @Test void explicitHavingAcceptsDeclaredModelMeasuresAndFiltersTheirAggregate() {
        var request = request(); request.setHaving(List.of(range()));
        var sql = queries.generateSql("FactSalesSemanticScaleQueryModel",request,SemanticRequestContext.empty()).getSql().toLowerCase();
        assertThat(sql).contains("having", "sum(");
        assertThat(sql.substring(sql.indexOf("having"))).contains("sum(", ">=", "<=");
    }
    @Test void sameMeasureSliceRemainsADetailWherePredicate() {
        var request = request(); request.setSlice(List.of(range()));
        var sql = queries.generateSql("FactSalesSemanticScaleQueryModel",request,SemanticRequestContext.empty()).getSql().toLowerCase();
        assertThat(sql).contains("where", ">=", "<=").doesNotContain("having");
    }
}
