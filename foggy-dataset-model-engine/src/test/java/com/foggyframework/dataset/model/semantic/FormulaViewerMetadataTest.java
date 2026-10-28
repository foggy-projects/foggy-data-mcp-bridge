package com.foggyframework.dataset.model.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Real QM loader -> semantic metadata, including model-only JSON ownership. @since 9.5.5 */
class FormulaViewerMetadataTest extends EcommerceTestSupport {
    @Resource private SemanticServiceV3 semanticService;

    @Test
    @SuppressWarnings("unchecked")
    void formulaShapesPreserveOnlyTheGovernedViewerWithoutChangingAggregation() {
        var request = new SemanticMetadataRequest();
        request.setQmModels(List.of("FormulaViewerMetadataQuery"));
        var response = semanticService.getMetadata(request, "json", SemanticRequestContext.empty());
        var fields = (Map<String, Map<String, Object>>) response.getData().get("fields");
        for (String name : List.of("rowVolume", "volumeTotal", "scalarVolume", "windowVolume")) {
            var field = fields.get(name);
            assertNotNull(field, name);
            assertEquals(Map.of("viewer", Map.of("format", "number", "precision", 4)), field.get("extData"), name);
            assertEquals("DOUBLE", field.get("type"));
            assertEquals(true, field.get("calculated"));
        }
        assertEquals("SUM", fields.get("rowVolume").get("aggregation"));
        assertEquals(true, fields.get("rowVolume").get("aggregatable"));
        assertFalse(fields.get("volumeTotal").containsKey("aggregation"));
        assertEquals(false, fields.get("scalarVolume").get("aggregatable"));
        assertNull(fields.get("plainNumber").get("extData"));
    }

    @Test
    void requestJsonCannotInjectModelPresentationMetadataAndSerializationKeepsItPrivate() throws Exception {
        var mapper = new ObjectMapper();
        var supplied = mapper.readValue("{\"name\":\"volumeTotal\",\"expression\":\"SUM(orderWeight)\","
                + "\"modelExtData\":{\"viewer\":{\"format\":\"money\",\"scaleFactor\":100}}}", CalculatedFieldDef.class);
        assertNull(supplied.getModelExtData());
        supplied.setModelExtData(Map.of("viewer", Map.of("format", "number", "precision", 4)));
        assertFalse(mapper.writeValueAsString(supplied).contains("modelExtData"));
        assertEquals("SUM(orderWeight)", supplied.getExpression());
    }
}
