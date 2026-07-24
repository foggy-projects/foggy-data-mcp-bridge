package com.foggyframework.dataset.model.engine.pivot.algo;

import com.foggyframework.dataset.model.spi.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PropertyResolver 单元测试
 *
 * <p>验证函数依赖校验逻辑：轴上有 PK → 允许属性贴合，否则拒绝。</p>
 */
@DisplayName("PropertyResolver 函数依赖验证")
class PropertyResolverTest {

    private static final Logger log = LoggerFactory.getLogger(PropertyResolverTest.class);

    @Test
    @DisplayName("合法：轴上有 dim$id，允许同维度 property")
    void testValidPropertyWithIdOnAxis() {
        QueryModel qm = mockQueryModel("product", "brand");

        Set<String> axisFields = new LinkedHashSet<>(List.of("product$id", "salesDate$month"));
        List<PropertyResolver.ResolvedProperty> result =
                PropertyResolver.resolve(qm, List.of("product$brand"), axisFields);

        assertEquals(1, result.size());
        assertEquals("product", result.get(0).getDimensionName());
        assertEquals("brand", result.get(0).getPropertyName());
        assertEquals("product$id", result.get(0).getLookupKeyField());
        log.info("合法验证通过: {}", result.get(0));
    }

    @Test
    @DisplayName("合法：多个 properties 指向同一维度")
    void testMultiplePropertiesSameDimension() {
        QueryModel qm = mockQueryModel("product", "brand", "productName");

        Set<String> axisFields = new LinkedHashSet<>(List.of("product$id"));
        List<PropertyResolver.ResolvedProperty> result =
                PropertyResolver.resolve(qm, List.of("product$brand", "product$productName"), axisFields);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("拒绝：轴上无维度 PK")
    void testRejectWithoutPkOnAxis() {
        QueryModel qm = mockQueryModel("product", "brand");

        // 轴上只有 product$categoryName 而非 product$id
        Set<String> axisFields = new LinkedHashSet<>(List.of("product$categoryName", "salesDate$month"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PropertyResolver.resolve(qm, List.of("product$brand"), axisFields));

        assertTrue(ex.getMessage().contains("无法证明函数依赖"));
        assertTrue(ex.getMessage().contains("product$id"));
        log.info("拒绝验证通过: {}", ex.getMessage());
    }

    @Test
    @DisplayName("拒绝：property 格式不合法（无 $ 分隔符）")
    void testRejectInvalidFormat() {
        QueryModel qm = mockQueryModel("product", "brand");

        Set<String> axisFields = new LinkedHashSet<>(List.of("product$id"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PropertyResolver.resolve(qm, List.of("brand"), axisFields));

        assertTrue(ex.getMessage().contains("格式不合法"));
    }

    @Test
    @DisplayName("拒绝：维度不存在")
    void testRejectNonExistentDimension() {
        QueryModel qm = mock(QueryModel.class);
        when(qm.findDimension("nonExistent")).thenReturn(null);

        Set<String> axisFields = new LinkedHashSet<>(List.of("nonExistent$id"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PropertyResolver.resolve(qm, List.of("nonExistent$brand"), axisFields));

        assertTrue(ex.getMessage().contains("不存在的维度"));
    }

    @Test
    @DisplayName("拒绝：属性不存在")
    void testRejectNonExistentProperty() {
        DbDimension dim = mock(DbDimension.class);
        when(dim.findPropertyByName("noSuchProp")).thenReturn(null);

        QueryModel qm = mock(QueryModel.class);
        when(qm.findDimension("product")).thenReturn(dim);

        Set<String> axisFields = new LinkedHashSet<>(List.of("product$id"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PropertyResolver.resolve(qm, List.of("product$noSuchProp"), axisFields));

        assertTrue(ex.getMessage().contains("找不到名为"));
    }

    @Test
    @DisplayName("空 properties 返回空列表")
    void testEmptyProperties() {
        QueryModel qm = mock(QueryModel.class);
        Set<String> axisFields = Set.of("product$id");

        List<PropertyResolver.ResolvedProperty> result =
                PropertyResolver.resolve(qm, null, axisFields);
        assertTrue(result.isEmpty());

        result = PropertyResolver.resolve(qm, Collections.emptyList(), axisFields);
        assertTrue(result.isEmpty());
    }

    // ========== Mock 工厂 ==========

    private QueryModel mockQueryModel(String dimName, String... propNames) {
        DbDimension dim = mock(DbDimension.class);
        for (String propName : propNames) {
            DbProperty prop = mock(DbProperty.class);
            when(dim.findPropertyByName(propName)).thenReturn(prop);
        }

        QueryModel qm = mock(QueryModel.class);
        when(qm.findDimension(dimName)).thenReturn(dim);
        return qm;
    }
}
