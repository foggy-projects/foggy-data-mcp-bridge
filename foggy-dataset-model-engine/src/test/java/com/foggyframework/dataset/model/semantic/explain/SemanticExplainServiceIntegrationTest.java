package com.foggyframework.dataset.model.semantic.explain;

import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticExplainServiceIntegrationTest extends EcommerceTestSupport {

    @Resource
    private SemanticExplainService explainService;

    @Test
    void definitionUsesModelStructureWithoutCompilingOrExecuting() {
        SemanticExplainRequest request = new SemanticExplainRequest();
        request.setFields(List.of("amount"));
        request.setIncludePhysicalNames(true);

        SemanticExplainResponse response = explainService.explain(
                "FactOrderQueryModel",
                request,
                SemanticRequestContext.empty());

        assertThat(response.schemaVersion()).isEqualTo("foggy-semantic-explain/v1");
        assertThat(response.basis()).isEqualTo(SemanticExplainResponse.Basis.DEFINITION);
        assertThat(response.definitionTrace().fields()).hasSize(1);
        SemanticExplainResponse.FieldTrace amount = response.definitionTrace().fields().get(0);
        assertThat(amount.queryField()).isEqualTo("FactOrderQueryModel.amount");
        assertThat(amount.tableModel()).isEqualTo("FactOrderModel");
        assertThat(amount.fieldType()).isEqualTo("measure");
        assertThat(amount.lineage())
                .extracting(SemanticExplainResponse.LineageEdge::confidence)
                .contains(SemanticExplainResponse.Confidence.EXACT);
        assertThat(response.materializationTrace().status())
                .isEqualTo(SemanticExplainResponse.StageStatus.NOT_EVALUATED);
        assertThat(response.executionTrace().jdbc())
                .isEqualTo(SemanticExplainResponse.StageStatus.NOT_EVALUATED);
        assertThat(response.sqlTrace().reasonCode()).isEqualTo("NOT_EVALUATED");
    }

    @Test
    void definitionDoesNotInferPhysicalLineageFromFormulaBuilder() {
        SemanticExplainRequest request = new SemanticExplainRequest();
        request.setFields(List.of("taxAmount2"));
        request.setIncludePhysicalNames(true);

        SemanticExplainResponse response = explainService.explain(
                "FactSalesQueryModel",
                request,
                SemanticRequestContext.empty());

        assertThat(response.definitionTrace().fields()).hasSize(1);
        assertThat(response.definitionTrace().fields().get(0).lineage())
                .extracting(SemanticExplainResponse.LineageEdge::reasonCode)
                .contains("FORMULA_BUILDER_OPAQUE", "FORMULA_BUILDER_SOURCE_OPAQUE")
                .doesNotContain("TABLE_COLUMN_MODEL_STRUCTURE");
        assertThat(response.limitations())
                .extracting(SemanticExplainResponse.Limitation::code)
                .contains("FORMULA_BUILDER_OPAQUE");
    }

    @Test
    void recompiledKeepsConditionProvenanceAndRedactsValuesWithoutJdbc() {
        SemanticQueryRequest payload = new SemanticQueryRequest();
        payload.setColumns(List.of("orderId", "amount"));
        SemanticQueryRequest.SliceItem orderTime = new SemanticQueryRequest.SliceItem();
        orderTime.setField("orderTime");
        orderTime.setOp("[)");
        orderTime.setValue(List.of("2024-01-01", "2024-02-01"));
        SemanticQueryRequest.SliceItem status = new SemanticQueryRequest.SliceItem();
        status.setField("orderStatus");
        status.setOp("=");
        status.setValue("COMPLETED");
        payload.setSlice(List.of(orderTime, status));
        payload.setLimit(5);
        payload.setExtData(java.util.Map.of("callerSecret", "must-not-leak"));

        SemanticExplainRequest request = new SemanticExplainRequest();
        request.setPayload(payload);
        request.setIncludeSql(true);
        request.setIncludePhysicalNames(true);

        SemanticExplainResponse response = explainService.explain(
                "FactOrderQueryModel",
                request,
                SemanticRequestContext.empty());

        assertThat(response.basis()).isEqualTo(SemanticExplainResponse.Basis.RECOMPILED);
        assertThat(response.compilationTrace().originalDsl().getSlice().get(0).getValue())
                .isEqualTo("***");
        assertThat(response.compilationTrace().originalDsl().getExtData())
                .containsEntry("callerSecret", "***");
        assertThat(response.compilationTrace().normalizedDsl().getSlice().get(0).getValue())
                .isEqualTo("***");
        assertThat(response.compilationTrace().normalizedConditions())
                .extracting(SemanticExplainResponse.ConditionTrace::origin)
                .containsExactly(
                        SemanticExplainResponse.ConditionOrigin.USER_SLICE,
                        SemanticExplainResponse.ConditionOrigin.USER_SLICE);
        assertThat(response.compilationTrace().events()).isEmpty();
        assertThat(response.sqlTrace().sourceOfTruth()).isEqualTo("QueryExecutionContext.sql");
        assertThat(response.sqlTrace().confidence())
                .isEqualTo(SemanticExplainResponse.Confidence.OBSERVED);
        assertThat(response.sqlTrace().reasonCode())
                .isEqualTo("SQL_CAPTURED_FROM_QUERY_EXECUTION_CONTEXT");
        assertThat(response.sqlTrace().finalPhysicalSql()).isNotBlank();
        assertThat(response.sqlTrace().parameters())
                .allSatisfy(parameter -> assertThat(parameter.redactedValue()).isEqualTo("***"));
        assertThat(response.sqlTrace().parameters())
                .extracting(SemanticExplainResponse.ParameterTrace::origin)
                .containsExactly(
                        SemanticExplainResponse.ConditionOrigin.USER_SLICE,
                        SemanticExplainResponse.ConditionOrigin.USER_SLICE,
                        SemanticExplainResponse.ConditionOrigin.USER_SLICE);
        assertThat(response.executionTrace().l1Cache())
                .isEqualTo(SemanticExplainResponse.StageStatus.NOT_EVALUATED);
        assertThat(response.executionTrace().l2Cache())
                .isEqualTo(SemanticExplainResponse.StageStatus.NOT_EVALUATED);
        assertThat(response.executionTrace().jdbc())
                .isEqualTo(SemanticExplainResponse.StageStatus.NOT_EVALUATED);
        assertThat(response.limitations())
                .extracting(SemanticExplainResponse.Limitation::code)
                .contains("RECOMPILED_NOT_EXECUTED_TRACE");
    }

    @Test
    void recompiledCapturesCandidateReasonsAndRewrittenSqlFromExecutionContext() {
        SemanticQueryRequest payload = new SemanticQueryRequest();
        payload.setColumns(List.of("product$categoryName", "salesAmount"));
        payload.setGroupBy(List.of(
                new SemanticQueryRequest.GroupByItem("product$categoryName", null)));
        payload.setLimit(20);

        SemanticExplainRequest request = new SemanticExplainRequest();
        request.setPayload(payload);
        request.setDepth(SemanticExplainRequest.Depth.DETAILED);
        request.setIncludeSql(true);
        request.setIncludePhysicalNames(true);

        SemanticExplainResponse response = explainService.explain(
                "FactSalesPreAggQueryModel",
                request,
                SemanticRequestContext.empty());

        assertThat(response.basis()).isEqualTo(SemanticExplainResponse.Basis.RECOMPILED);
        assertThat(response.materializationTrace().status())
                .isEqualTo(SemanticExplainResponse.StageStatus.EVALUATED);
        assertThat(response.materializationTrace().route()).isEqualTo("PREAGG_ROLLUP");
        assertThat(response.materializationTrace().preAggregation())
                .isEqualTo("monthly_category_sales");
        assertThat(response.materializationTrace().reasonCode()).isEqualTo("PREAGG_ROLLUP");
        assertThat(response.compilationTrace().events())
                .filteredOn(event -> "PRE_AGGREGATION_CANDIDATE_EVALUATED".equals(event.event()))
                .extracting(ExplainTraceCollector.Event::reasonCode)
                .contains(
                        "PREAGG_HYBRID_WATERMARK_MISSING",
                        "PREAGG_DIMENSION_MISSING",
                        "PREAGG_ROLLUP");
        assertThat(response.sqlTrace().logicalSql()).contains("fact_sales");
        assertThat(response.sqlTrace().finalPhysicalSql())
                .contains("preagg_monthly_category_sales")
                .isNotEqualTo(response.sqlTrace().logicalSql());
        assertThat(response.sqlTrace().sourceOfTruth()).isEqualTo("QueryExecutionContext.sql");
        assertThat(response.sqlTrace().confidence())
                .isEqualTo(SemanticExplainResponse.Confidence.OBSERVED);
        assertThat(response.sqlTrace().reasonCode())
                .isEqualTo("SQL_CAPTURED_FROM_QUERY_EXECUTION_CONTEXT");
        assertThat(response.executionTrace().l1Cache())
                .isEqualTo(SemanticExplainResponse.StageStatus.NOT_EVALUATED);
        assertThat(response.executionTrace().l2Cache())
                .isEqualTo(SemanticExplainResponse.StageStatus.NOT_EVALUATED);
        assertThat(response.executionTrace().jdbc())
                .isEqualTo(SemanticExplainResponse.StageStatus.NOT_EVALUATED);
    }

    @Test
    void recompiledReportsStableOverallReasonWhenEveryCandidateRejectsTheSameShape() {
        SemanticQueryRequest payload = new SemanticQueryRequest();
        payload.setColumns(List.of("store$caption", "salesAmount"));
        payload.setGroupBy(List.of(
                new SemanticQueryRequest.GroupByItem("store$caption", null)));

        SemanticExplainRequest request = new SemanticExplainRequest();
        request.setPayload(payload);
        request.setDepth(SemanticExplainRequest.Depth.DETAILED);

        SemanticExplainResponse response = explainService.explain(
                "FactSalesPreAggQueryModel",
                request,
                SemanticRequestContext.empty());

        assertThat(response.materializationTrace().route()).isEqualTo("RAW");
        assertThat(response.materializationTrace().decision())
                .isEqualTo("SOURCE_QUERY_RETAINED");
        assertThat(response.materializationTrace().reasonCode())
                .isEqualTo("PREAGG_DIMENSION_MISSING");
        assertThat(response.compilationTrace().events())
                .filteredOn(event -> "PRE_AGGREGATION_CANDIDATE_EVALUATED".equals(event.event()))
                .hasSize(3)
                .allSatisfy(event -> {
                    assertThat(event.decision()).isEqualTo("REJECTED");
                    assertThat(event.reasonCode()).isEqualTo("PREAGG_DIMENSION_MISSING");
                    assertThat(event.details()).containsKey("preAggregation");
                });
        assertThat(response.executionTrace().route()).isEqualTo("NOT_EVALUATED");
    }

    @Test
    void timeWindowRecompilesThroughExistingComposeCompilerAndMarksPreAggNotEvaluated() {
        SemanticQueryRequest payload = new SemanticQueryRequest();
        payload.setColumns(List.of(
                "salesDate$year",
                "salesDate$month",
                "salesAmount",
                "salesAmount__prior",
                "salesAmount__diff",
                "salesAmount__ratio"));
        payload.setGroupBy(List.of(
                new SemanticQueryRequest.GroupByItem("salesDate$year", null),
                new SemanticQueryRequest.GroupByItem("salesDate$month", null)));
        payload.setTimeWindow(java.util.Map.of(
                "field", "salesDate$id",
                "grain", "month",
                "comparison", "yoy",
                "targetMetrics", List.of("salesAmount")));
        payload.setLimit(20);

        SemanticExplainRequest request = new SemanticExplainRequest();
        request.setPayload(payload);
        request.setDepth(SemanticExplainRequest.Depth.DETAILED);
        request.setIncludeSql(true);
        request.setIncludePhysicalNames(true);

        SemanticExplainResponse response = explainService.explain(
                "FactSalesQueryModel",
                request,
                SemanticRequestContext.empty());

        assertThat(response.basis()).isEqualTo(SemanticExplainResponse.Basis.RECOMPILED);
        assertThat(response.sqlTrace().sourceOfTruth()).isEqualTo("ComposeSqlCompiler.output");
        assertThat(response.sqlTrace().confidence())
                .isEqualTo(SemanticExplainResponse.Confidence.OBSERVED);
        assertThat(response.sqlTrace().reasonCode())
                .isEqualTo("SQL_CAPTURED_FROM_COMPOSE_COMPILER");
        assertThat(response.sqlTrace().finalPhysicalSql()).isNotBlank();
        assertThat(response.materializationTrace().status())
                .isEqualTo(SemanticExplainResponse.StageStatus.NOT_EVALUATED);
        assertThat(response.materializationTrace().route()).isEqualTo("NOT_EVALUATED");
        assertThat(response.materializationTrace().reasonCode())
                .isEqualTo("PREAGG_NOT_EVALUATED_FOR_TIME_WINDOW_COMPOSE");
        assertThat(response.compilationTrace().events())
                .extracting(ExplainTraceCollector.Event::reasonCode)
                .contains("TIME_WINDOW_COMPOSE_PLAN_COMPILED");
        assertThat(response.compilationTrace().originalDsl().getTimeWindow())
                .containsEntry("field", "***")
                .containsEntry("grain", "***")
                .containsEntry("comparison", "***");
        assertThat(response.compilationTrace().originalDsl().getTimeWindow().get("targetMetrics"))
                .isEqualTo(List.of("***"));
    }
}
