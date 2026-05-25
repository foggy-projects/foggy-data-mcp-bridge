package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RealModelDictionaryDiscoveryMetadataTest extends EcommerceTestSupport {

    @Resource
    private SemanticServiceV3 semanticServiceV3;

    @Test
    @DisplayName("FactOrderQueryModel 输出 paymentStatus 的运行时字典发现")
    void factOrderMetadataIncludesPaymentStatusDiscovery() {
        Map<String, Object> discovery = discoveryFor("FactOrderQueryModel", "paymentStatus");

        assertSampledGroupBy(discovery);
        assertDiscoveredValuesContain(discovery, "PAID", "UNPAID");
        assertAliasValues(discovery, "not_fully_paid", "UNPAID", "PARTIAL");
        assertAliasValues(discovery, "settled_or_refunded", "PAID", "REFUNDED");
    }

    @Test
    @DisplayName("OdooSaleOrderQueryModel 输出 state 的运行时字典发现")
    void odooSaleOrderMetadataIncludesStateDiscovery() {
        Map<String, Object> discovery = discoveryFor("OdooSaleOrderQueryModel", "state");

        assertSampledGroupBy(discovery);
        assertDiscoveredValuesContain(discovery, "sale", "draft", "cancel");
        assertAliasValues(discovery, "active_sales_order", "sale", "done");
        assertAliasValues(discovery, "quotation", "draft", "sent");
    }

    @Test
    @DisplayName("OdooAccountMoveQueryModel 输出 paymentState 的运行时字典发现")
    void odooAccountMoveMetadataIncludesPaymentStateDiscovery() {
        Map<String, Object> discovery = discoveryFor("OdooAccountMoveQueryModel", "paymentState");

        assertSampledGroupBy(discovery);
        assertDiscoveredValuesContain(discovery, "paid", "not_paid", "partial");
        assertAliasValues(discovery, "open_receivable", "not_paid", "partial", "in_payment");
        assertAliasValues(discovery, "closed_receivable", "paid", "reversed", "invoicing_legacy");
    }

    private Map<String, Object> discoveryFor(String modelName, String fieldName) {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(List.of(modelName));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                request, "json", SemanticRequestContext.empty());
        assertNotNull(response);
        assertNotNull(response.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) response.getData().get("fields");
        assertNotNull(fields, "metadata 应输出 fields");

        @SuppressWarnings("unchecked")
        Map<String, Object> field = (Map<String, Object>) fields.get(fieldName);
        assertNotNull(field, modelName + "." + fieldName + " 应暴露在 metadata 中");

        @SuppressWarnings("unchecked")
        Map<String, Object> discovery = (Map<String, Object>) field.get("dictionaryDiscovery");
        assertNotNull(discovery, modelName + "." + fieldName + " 应包含 dictionaryDiscovery metadata");
        return discovery;
    }

    private void assertSampledGroupBy(Map<String, Object> discovery) {
        assertEquals(Boolean.TRUE, discovery.get("enabled"));
        assertEquals("group_by", discovery.get("strategy"));
        assertEquals(20, ((Number) discovery.get("maxValues")).intValue());
        assertEquals(3600L, ((Number) discovery.get("refreshTtlSeconds")).longValue());
        assertEquals("runtime_observed", discovery.get("valuesSource"));
        assertEquals("sampled", discovery.get("valuesStatus"));
        assertNotNull(discovery.get("sampledAt"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) discovery.get("values");
        assertNotNull(values, "运行时发现值不能为空");
        assertFalse(values.isEmpty(), "运行时发现值不能为空");
        assertTrue(values.stream().allMatch(value -> value.containsKey("count")),
                "group_by 策略应输出每个值的计数");
    }

    private void assertDiscoveredValuesContain(Map<String, Object> discovery, String... expectedValues) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) discovery.get("values");
        List<Object> actualValues = values.stream()
                .map(value -> value.get("value"))
                .toList();
        for (String expected : expectedValues) {
            assertTrue(actualValues.contains(expected), "应发现真实字典值 " + expected);
        }
    }

    private void assertAliasValues(Map<String, Object> discovery, String aliasName, Object... expectedValues) {
        @SuppressWarnings("unchecked")
        Map<String, Object> aliases = (Map<String, Object>) discovery.get("aliases");
        assertNotNull(aliases, "应输出治理别名");

        @SuppressWarnings("unchecked")
        Map<String, Object> alias = (Map<String, Object>) aliases.get(aliasName);
        assertNotNull(alias, "应输出治理别名 " + aliasName);
        assertEquals(List.of(expectedValues), alias.get("values"));
    }
}
