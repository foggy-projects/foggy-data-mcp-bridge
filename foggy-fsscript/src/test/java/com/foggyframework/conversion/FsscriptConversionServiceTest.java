package com.foggyframework.conversion;

import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FsscriptConversionServiceTest {

    @Test
    @DisplayName("Map<String, Object> 中的嵌套 Map 应保持结构")
    void nestedMapStoredAsObject_keepsEntries() {
        Map<String, Object> viewer = new LinkedHashMap<>();
        viewer.put("format", "money");
        viewer.put("scaleFactor", 100);

        Map<String, Object> extData = new LinkedHashMap<>();
        extData.put("viewer", viewer);

        Map<String, Object> source = Map.of("extData", extData);
        Holder converted = FsscriptConversionService.getSharedInstance().convert(source, Holder.class);

        Map<?, ?> convertedViewer = assertInstanceOf(Map.class, converted.getExtData().get("viewer"));
        assertEquals("money", convertedViewer.get("format"));
        assertEquals(100, convertedViewer.get("scaleFactor"));
    }

    @Data
    public static class Holder {
        private Map<String, Object> extData;
    }
}
