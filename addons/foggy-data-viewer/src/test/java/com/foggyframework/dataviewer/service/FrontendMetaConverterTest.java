package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.FrontendMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("frontend-meta viewer 扩展转换测试")
class FrontendMetaConverterTest {

    @Test
    @DisplayName("仅透传 extData.viewer 并保持无配置字段兼容")
    void convert_whitelistsViewerExtension() {
        Map<String, Object> viewer = new LinkedHashMap<>();
        viewer.put("format", "money");
        viewer.put("rawUnit", "minor");
        viewer.put("displayUnit", "CNY");
        viewer.put("scaleFactor", 100);
        viewer.put("precision", 2);
        viewer.put("nestedPrivate", Map.of("secret", true));

        Map<String, Object> moneyField = new LinkedHashMap<>();
        moneyField.put("fieldName", "totalTransportFee");
        moneyField.put("name", "运输费");
        moneyField.put("type", "MONEY");
        moneyField.put("measure", true);
        moneyField.put("extData", Map.of(
                "viewer", viewer,
                "internalOnly", "must-not-reach-frontend"));

        Map<String, Object> plainField = new LinkedHashMap<>();
        plainField.put("fieldName", "amountYuan");
        plainField.put("name", "AI 金额");
        plainField.put("type", "MONEY");

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("totalTransportFee", moneyField);
        fields.put("amountYuan", plainField);

        FrontendMeta result = new FrontendMetaConverter().convert(Map.of(
                "models", Map.of("TmsQueryModel", Map.of("name", "TMS")),
                "fields", fields));

        assertNotNull(result);
        assertEquals(List.of("totalTransportFee", "amountYuan"),
                result.getFields().stream().map(FrontendMeta.FieldMeta::getName).toList());

        Map<String, Object> extData = result.getFields().get(0).getExtData();
        assertNotNull(extData);
        assertEquals(1, extData.size());
        assertFalse(extData.containsKey("internalOnly"));
        @SuppressWarnings("unchecked")
        Map<String, Object> convertedViewer = (Map<String, Object>) extData.get("viewer");
        assertEquals(5, convertedViewer.size());
        assertFalse(convertedViewer.containsKey("nestedPrivate"));
        assertEquals(100, convertedViewer.get("scaleFactor"));

        assertNull(result.getFields().get(1).getExtData());
    }

    @Test
    @DisplayName("字典 caption 保留不可排序语义且默认不生成重复可见列")
    void convert_preservesDictionaryCaptionCapabilities() {
        Map<String, Object> captionField = new LinkedHashMap<>();
        captionField.put("fieldName", "status$caption");
        captionField.put("name", "状态(名称)");
        captionField.put("type", "STRING");
        captionField.put("filterType", "text");
        captionField.put("filterable", true);
        captionField.put("sortable", false);
        captionField.put("semanticRole", "dictionary-caption");
        captionField.put("dictId", "order_status");

        FrontendMeta result = new FrontendMetaConverter().convert(Map.of(
                "models", Map.of("OrderQueryModel", Map.of("name", "订单")),
                "fields", Map.of("status$caption", captionField)));

        FrontendMeta.FieldMeta field = result.getFields().get(0);
        assertEquals("dictionary-caption", field.getCategory());
        assertFalse(field.getSortable());
        assertTrue(field.getFilterable());
        assertFalse(field.getUiHints().getVisible());
        assertNull(field.getMemberLookup());
    }
}
