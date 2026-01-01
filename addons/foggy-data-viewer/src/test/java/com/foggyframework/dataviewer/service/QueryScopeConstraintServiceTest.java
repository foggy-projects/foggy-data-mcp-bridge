package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryScopeConstraintService 单元测试
 */
class QueryScopeConstraintServiceTest {

    private DataViewerProperties properties;
    private QueryScopeConstraintService service;

    @BeforeEach
    void setUp() {
        properties = new DataViewerProperties();
        properties.setScopeConstraints(new DataViewerProperties.ScopeConstraintProperties());
        properties.getScopeConstraints().setEnabled(true);
        properties.getScopeConstraints().setDefaultMaxDurationDays(31);
        properties.getScopeConstraints().setModels(new HashMap<>());

        service = new QueryScopeConstraintService(properties);
    }

    @Nested
    @DisplayName("约束启用/禁用测试")
    class EnabledDisabledTests {

        @Test
        @DisplayName("当约束禁用时，不应进行任何验证")
        void shouldNotValidateWhenDisabled() {
            properties.getScopeConstraints().setEnabled(false);

            List<Map<String, Object>> slice = new ArrayList<>();
            // 空的过滤条件也不应抛异常

            List<Map<String, Object>> result = service.enforceConstraints("testModel", slice);
            assertNotNull(result);
        }

        @Test
        @DisplayName("当约束启用但没有slice参数时，应抛出异常")
        void shouldThrowWhenNoSliceAndEnabled() {
            List<Map<String, Object>> slice = new ArrayList<>();

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.enforceConstraints("testModel", slice)
            );

            assertTrue(exception.getMessage().contains("filter"));
        }

        @Test
        @DisplayName("当slice为null时，应抛出异常")
        void shouldThrowWhenSliceIsNull() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.enforceConstraints("testModel", null)
            );

            assertTrue(exception.getMessage().contains("filter"));
        }
    }

    @Nested
    @DisplayName("slice参数验证测试")
    class SliceValidationTests {

        @Test
        @DisplayName("当slice为空List时，应抛出异常")
        void shouldThrowWhenSliceIsEmpty() {
            List<Map<String, Object>> slice = new ArrayList<>();

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.enforceConstraints("testModel", slice)
            );

            assertTrue(exception.getMessage().contains("filter"));
        }

        @Test
        @DisplayName("当slice包含有效过滤条件时，应通过验证")
        void shouldPassWhenSliceHasValidFilters() {
            List<Map<String, Object>> slice = new ArrayList<>();
            Map<String, Object> filter = new HashMap<>();
            filter.put("field", "customerId");
            filter.put("op", "=");
            filter.put("value", "C001");
            slice.add(filter);

            assertDoesNotThrow(() -> service.enforceConstraints("testModel", slice));
        }
    }

    @Nested
    @DisplayName("模型特定配置测试")
    class ModelSpecificConfigTests {

        @Test
        @DisplayName("当模型有特定配置时，应验证scopeField")
        void shouldValidateScopeField() {
            DataViewerProperties.ModelScopeConstraint modelConfig =
                    new DataViewerProperties.ModelScopeConstraint();
            modelConfig.setScopeField("orderDate");
            modelConfig.setMaxDurationDays(7);
            properties.getScopeConstraints().getModels().put("orders", modelConfig);

            List<Map<String, Object>> slice = new ArrayList<>();
            Map<String, Object> filter = new HashMap<>();
            filter.put("field", "customerId");
            filter.put("op", "=");
            filter.put("value", "C001");
            slice.add(filter);

            // 没有orderDate过滤条件，应抛异常
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.enforceConstraints("orders", slice)
            );

            assertTrue(exception.getMessage().contains("orderDate"));
        }

        @Test
        @DisplayName("当提供了scopeField过滤条件时，应通过验证")
        void shouldPassWhenScopeFieldPresent() {
            DataViewerProperties.ModelScopeConstraint modelConfig =
                    new DataViewerProperties.ModelScopeConstraint();
            modelConfig.setScopeField("orderDate");
            modelConfig.setMaxDurationDays(30);
            properties.getScopeConstraints().getModels().put("orders", modelConfig);

            List<Map<String, Object>> slice = new ArrayList<>();
            Map<String, Object> filter = new HashMap<>();
            filter.put("field", "orderDate");
            filter.put("op", ">=");
            filter.put("value", LocalDate.now().minusDays(7).toString());
            slice.add(filter);

            List<Map<String, Object>> result = service.enforceConstraints("orders", slice);
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("日期范围约束测试")
    class DateRangeConstraintTests {

        @Test
        @DisplayName("当日期范围超过最大限制时，应抛出异常")
        void shouldThrowWhenDateRangeExceedsMax() {
            DataViewerProperties.ModelScopeConstraint modelConfig =
                    new DataViewerProperties.ModelScopeConstraint();
            modelConfig.setScopeField("orderDate");
            modelConfig.setMaxDurationDays(7);
            properties.getScopeConstraints().getModels().put("orders", modelConfig);

            List<Map<String, Object>> slice = new ArrayList<>();

            // 开始日期
            Map<String, Object> startFilter = new HashMap<>();
            startFilter.put("field", "orderDate");
            startFilter.put("op", ">=");
            startFilter.put("value", LocalDate.now().minusDays(30).toString());
            slice.add(startFilter);

            // 结束日期
            Map<String, Object> endFilter = new HashMap<>();
            endFilter.put("field", "orderDate");
            endFilter.put("op", "<=");
            endFilter.put("value", LocalDate.now().toString());
            slice.add(endFilter);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.enforceConstraints("orders", slice)
            );

            assertTrue(exception.getMessage().contains("7 days"));
        }

        @Test
        @DisplayName("当只有开始日期时，应自动添加结束日期约束")
        void shouldAutoAddEndDateWhenOnlyStartDate() {
            DataViewerProperties.ModelScopeConstraint modelConfig =
                    new DataViewerProperties.ModelScopeConstraint();
            modelConfig.setScopeField("orderDate");
            modelConfig.setMaxDurationDays(7);
            properties.getScopeConstraints().getModels().put("orders", modelConfig);

            List<Map<String, Object>> slice = new ArrayList<>();

            Map<String, Object> startFilter = new HashMap<>();
            startFilter.put("field", "orderDate");
            startFilter.put("op", ">=");
            startFilter.put("value", LocalDate.now().minusDays(3).toString());
            slice.add(startFilter);

            List<Map<String, Object>> result = service.enforceConstraints("orders", slice);

            // 应该添加了结束日期过滤条件
            assertEquals(2, result.size());

            // 验证第二个过滤条件是结束日期
            Map<String, Object> addedFilter = result.get(1);
            assertEquals("orderDate", addedFilter.get("field"));
            assertEquals("<", addedFilter.get("op"));
        }

        @Test
        @DisplayName("当日期范围在限制内时，不应调整")
        void shouldNotAdjustWhenDateRangeWithinLimit() {
            DataViewerProperties.ModelScopeConstraint modelConfig =
                    new DataViewerProperties.ModelScopeConstraint();
            modelConfig.setScopeField("orderDate");
            modelConfig.setMaxDurationDays(30);
            properties.getScopeConstraints().getModels().put("orders", modelConfig);

            LocalDate startDate = LocalDate.now().minusDays(15);
            LocalDate endDate = LocalDate.now();

            List<Map<String, Object>> slice = new ArrayList<>();

            Map<String, Object> startFilter = new HashMap<>();
            startFilter.put("field", "orderDate");
            startFilter.put("op", ">=");
            startFilter.put("value", startDate.toString());
            slice.add(startFilter);

            Map<String, Object> endFilter = new HashMap<>();
            endFilter.put("field", "orderDate");
            endFilter.put("op", "<=");
            endFilter.put("value", endDate.toString());
            slice.add(endFilter);

            List<Map<String, Object>> result = service.enforceConstraints("orders", slice);

            // 应该保持原样，不添加新的过滤条件
            assertEquals(2, result.size());
        }
    }
}
