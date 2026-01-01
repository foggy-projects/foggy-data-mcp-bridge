package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryScopeConstraintService 单元测试
 * <p>
 * 使用类型安全的 SliceRequestDef
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

            List<SliceRequestDef> slice = new ArrayList<>();
            // 空的过滤条件也不应抛异常

            List<SliceRequestDef> result = service.enforceConstraints("testModel", slice);
            assertNotNull(result);
        }

        @Test
        @DisplayName("当约束启用但没有slice参数时，应抛出异常")
        void shouldThrowWhenNoSliceAndEnabled() {
            List<SliceRequestDef> slice = new ArrayList<>();

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
            List<SliceRequestDef> slice = new ArrayList<>();

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.enforceConstraints("testModel", slice)
            );

            assertTrue(exception.getMessage().contains("filter"));
        }

        @Test
        @DisplayName("当slice包含有效过滤条件时，应通过验证")
        void shouldPassWhenSliceHasValidFilters() {
            List<SliceRequestDef> slice = new ArrayList<>();
            slice.add(new SliceRequestDef("customerId", "=", "C001"));

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

            List<SliceRequestDef> slice = new ArrayList<>();
            slice.add(new SliceRequestDef("customerId", "=", "C001"));

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

            List<SliceRequestDef> slice = new ArrayList<>();
            slice.add(new SliceRequestDef("orderDate", ">=", LocalDate.now().minusDays(7).toString()));

            List<SliceRequestDef> result = service.enforceConstraints("orders", slice);
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

            List<SliceRequestDef> slice = new ArrayList<>();
            slice.add(new SliceRequestDef("orderDate", ">=", LocalDate.now().minusDays(30).toString()));
            slice.add(new SliceRequestDef("orderDate", "<=", LocalDate.now().toString()));

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

            List<SliceRequestDef> slice = new ArrayList<>();
            slice.add(new SliceRequestDef("orderDate", ">=", LocalDate.now().minusDays(3).toString()));

            List<SliceRequestDef> result = service.enforceConstraints("orders", slice);

            // 应该添加了结束日期过滤条件
            assertEquals(2, result.size());

            // 验证第二个过滤条件是结束日期
            SliceRequestDef addedFilter = result.get(1);
            assertEquals("orderDate", addedFilter.getField());
            assertEquals("<", addedFilter.getOp());
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

            List<SliceRequestDef> slice = new ArrayList<>();
            slice.add(new SliceRequestDef("orderDate", ">=", startDate.toString()));
            slice.add(new SliceRequestDef("orderDate", "<=", endDate.toString()));

            List<SliceRequestDef> result = service.enforceConstraints("orders", slice);

            // 应该保持原样，不添加新的过滤条件
            assertEquals(2, result.size());
        }
    }
}
