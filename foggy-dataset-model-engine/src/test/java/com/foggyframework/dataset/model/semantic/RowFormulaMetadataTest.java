package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Model-owned row aggregation is advertised without changing ordinary formula metadata. @since 9.5.5 */
class RowFormulaMetadataTest extends EcommerceTestSupport {
    private static final String MODEL = "RowFormulaMeasurementQuery";
    @Resource private SemanticServiceV3 semanticService;

    @Test
    void defaultRowAggregationIsExposedAsAnAggregatableMeasure() {
        var field = fields().get("weight");

        assertEquals(true, field.get("measure"));
        assertEquals(true, field.get("aggregatable"));
        assertEquals(true, field.get("filterable"));
        assertEquals("SUM", field.get("aggregation"));
        assertEquals(true, field.get("calculated"));
        assertEquals(true, field.get("predefined"));
    }

    @Test
    void ordinaryFormulaRetainsItsPreviousMetadata() {
        var field = fields().get("doubleWeight");

        assertEquals(false, field.get("measure"));
        assertEquals(false, field.get("aggregatable"));
        assertEquals(false, field.get("filterable"));
        assertFalse(field.containsKey("aggregation"));
        assertEquals(true, field.get("calculated"));
        assertEquals(true, field.get("predefined"));
    }

    @Test
    void singleAndMultiModelMarkdownAdvertiseTheDefaultAggregation() {
        var single = semanticService.getMetadata(request(List.of(MODEL)), "markdown", SemanticRequestContext.empty());
        var multiple = semanticService.getMetadata(request(List.of(MODEL, "FactOrderQueryModel")), "markdown", SemanticRequestContext.empty());

        assertTrue(single.getContent().contains("默认聚合: SUM"));
        assertTrue(multiple.getContent().contains("默认聚合: SUM"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> fields() {
        var response = semanticService.getMetadata(request(List.of(MODEL)), "json", SemanticRequestContext.empty());
        return (Map<String, Map<String, Object>>) response.getData().get("fields");
    }

    private SemanticMetadataRequest request(List<String> models) {
        var request = new SemanticMetadataRequest();
        request.setQmModels(models);
        return request;
    }
}
