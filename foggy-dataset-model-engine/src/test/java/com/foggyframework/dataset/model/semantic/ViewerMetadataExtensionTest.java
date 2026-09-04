package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.spi.DbMeasure;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.TableModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DataViewer 字段扩展透传集成测试")
class ViewerMetadataExtensionTest extends EcommerceTestSupport {

    private static final String TABLE_MODEL = "FactSalesViewerMetadataModel";
    private static final String QUERY_MODEL = "FactSalesViewerMetadataQueryModel";

    @Resource
    private SemanticServiceV3 semanticServiceV3;

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("TM 与 QM 加载应保留 extData.viewer")
    void modelLoading_keepsViewerExtension() {
        TableModel tableModel = tableModelLoaderManager.load(TABLE_MODEL);
        DbMeasure measure = tableModel.findJdbcMeasureByName("salesAmountMinor");
        assertNotNull(measure);
        Map<String, Object> measureExtData = castMap(measure.getExtData());
        assertViewer(measureExtData);
        assertEquals("must-not-reach-frontend", measureExtData.get("internalOnly"));

        QueryModelSupport queryModel = (QueryModelSupport) getQueryModel(QUERY_MODEL);
        DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName("salesAmountMinor", true);
        Map<String, Object> queryExtData = castMap(queryColumn.getExtData());
        assertViewer(queryExtData);
        assertEquals("must-not-reach-frontend", queryExtData.get("qmInternalOnly"));
    }

    @Test
    @DisplayName("V3 元数据仅应透传白名单 viewer")
    void metadata_exposesOnlyViewerExtension() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(QUERY_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                request, "json", SemanticRequestContext.empty());

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) response.getData().get("fields");
        @SuppressWarnings("unchecked")
        Map<String, Object> amount = (Map<String, Object>) fields.get("salesAmountMinor");
        assertNotNull(amount);
        assertViewer(castMap(amount.get("extData")));
        assertEquals(1, castMap(amount.get("extData")).size());
        assertFalse(castMap(amount.get("extData")).containsKey("internalOnly"));
        assertFalse(castMap(amount.get("extData")).containsKey("qmInternalOnly"));
    }

    @Test
    @DisplayName("查询结果应保持数据库原始值")
    void query_keepsRawPhysicalValue() {
        String sql = paginateSql("""
                SELECT sales_key AS salesKey, sales_amount AS salesAmountMinor
                FROM fact_sales
                WHERE sales_amount IS NOT NULL
                ORDER BY sales_key
                """, 1);
        Map<String, Object> expected = executeQuery(sql).get(0);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("salesKey", "salesAmountMinor"));
        SemanticQueryRequest.SliceItem slice = new SemanticQueryRequest.SliceItem();
        slice.setField("salesKey");
        slice.setOp("=");
        slice.setValue(expected.get("salesKey"));
        request.setSlice(List.of(slice));
        request.setLimit(1);

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                QUERY_MODEL, request, "execute", SemanticRequestContext.empty());

        assertNotNull(response.getItems());
        assertEquals(1, response.getItems().size());
        Object actualAmount = response.getItems().get(0).get("salesAmountMinor");
        assertEquals(0, new BigDecimal(String.valueOf(expected.get("salesAmountMinor")))
                .compareTo(new BigDecimal(String.valueOf(actualAmount))));
    }

    private void assertViewer(Map<String, Object> extData) {
        assertNotNull(extData);
        Map<String, Object> viewer = castMap(extData.get("viewer"));
        assertEquals("money", viewer.get("format"));
        assertEquals("minor", viewer.get("rawUnit"));
        assertEquals("CNY", viewer.get("displayUnit"));
        assertEquals(0, new BigDecimal("100").compareTo(
                new BigDecimal(String.valueOf(viewer.get("scaleFactor")))));
        assertEquals(2, ((Number) viewer.get("precision")).intValue());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<String, Object>) value;
    }
}
