package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DictionaryDiscoveryMetadataTest extends EcommerceTestSupport {

    private static final String TEST_MODEL = "DictionaryDiscoveryOrderQueryModel";

    @Resource
    private SemanticServiceV3 semanticServiceV3;

    @Test
    @DisplayName("JSON metadata 输出 orderStatus 的 dictionaryDiscovery 契约和模型别名")
    void jsonMetadata_includesDictionaryDiscoveryContractAndAliases() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(TEST_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                request, "json", SemanticRequestContext.empty());

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) response.getData().get("fields");
        assertTrue(fields.containsKey("status"), "应暴露 status 字段");

        @SuppressWarnings("unchecked")
        Map<String, Object> orderStatus = (Map<String, Object>) fields.get("status");
        @SuppressWarnings("unchecked")
        Map<String, Object> discovery = (Map<String, Object>) orderStatus.get("dictionaryDiscovery");

        assertNotNull(discovery, "orderStatus 应包含 dictionaryDiscovery metadata");
        assertEquals(Boolean.TRUE, discovery.get("enabled"));
        assertEquals("group_by", discovery.get("strategy"));
        assertEquals(2, ((Number) discovery.get("maxValues")).intValue());
        assertEquals(3600L, ((Number) discovery.get("refreshTtlSeconds")).longValue());
        assertEquals("runtime_observed", discovery.get("valuesSource"));
        assertEquals("sampled", discovery.get("valuesStatus"));
        assertEquals(Boolean.TRUE, discovery.get("truncated"));
        assertNotNull(discovery.get("sampledAt"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) discovery.get("values");
        assertNotNull(values, "应输出运行时发现值");
        assertEquals(2, values.size(), "应按 maxValues 截断");
        assertEquals("COMPLETED", values.get(0).get("value"), "应优先输出最高频真实状态");
        assertTrue(values.stream().anyMatch(v -> "COMPLETED".equals(v.get("value"))),
                "应包含数据库真实订单状态");
        assertTrue(values.stream().allMatch(v -> v.containsKey("count")),
                "group_by 策略应输出每个字典值的计数");
        assertTrue(((Number) values.get(0).get("count")).longValue()
                        >= ((Number) values.get(1).get("count")).longValue(),
                "group_by 发现值应按出现次数降序输出");

        @SuppressWarnings("unchecked")
        Map<String, Object> aliases = (Map<String, Object>) discovery.get("aliases");
        assertNotNull(aliases, "应输出模型治理别名");

        @SuppressWarnings("unchecked")
        Map<String, Object> openOrder = (Map<String, Object>) aliases.get("open_order");
        assertNotNull(openOrder, "应包含 open_order 别名");
        assertEquals(List.of("PENDING", "CONFIRMED", "PROCESSING"), openOrder.get("values"));
    }

    @Test
    @DisplayName("Markdown metadata 输出运行时字典发现说明")
    void markdownMetadata_includesDictionaryDiscoverySummary() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(TEST_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                request, "markdown", SemanticRequestContext.empty());

        String content = response.getContent();
        assertNotNull(content);
        assertTrue(content.contains("运行时字典发现"));
        assertTrue(content.contains("values:"));
        assertTrue(content.contains("open_order"));
        assertTrue(content.contains("PENDING"));
        assertTrue(content.contains("CONFIRMED"));
        assertTrue(content.contains("PROCESSING"));
    }

    @Test
    @DisplayName("fieldAccess 裁剪后不暴露 orderStatus 的 dictionaryDiscovery")
    void fieldAccess_filtersDictionaryDiscoveryWithField() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(TEST_MODEL));

        SemanticRequestContext context = SemanticRequestContext.of(
                null, (ModelResultContext.SecurityContext) null, Set.of("orderId"));
        SemanticMetadataResponse response = semanticServiceV3.getMetadata(request, "json", context);

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) response.getData().get("fields");
        assertTrue(fields.containsKey("orderId"), "白名单字段应可见");
        assertFalse(fields.containsKey("status"), "非白名单字段不应暴露 discovery metadata");
    }
}
