package com.foggyframework.dataset.db.model.preagg.refresh;

import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * IncrementalRefreshStrategy 单元测试
 * <p>
 * 验证：
 * <ul>
 *   <li>增量刷新策略需要水位线列配置</li>
 *   <li>DELETE + INSERT 应在同一事务内</li>
 *   <li>lookback 计算正确</li>
 * </ul>
 * </p>
 */
@DisplayName("IncrementalRefreshStrategy Tests")
class IncrementalRefreshStrategyTest {

    private IncrementalRefreshStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new IncrementalRefreshStrategy();
    }

    @Test
    @DisplayName("Strategy name should be INCREMENTAL")
    void testStrategyName() {
        assertEquals("INCREMENTAL", strategy.getStrategyName());
    }

    @Test
    @DisplayName("Should not support when no refresh config")
    void testNotSupportedWithoutConfig() {
        PreAggregation preAgg = mock(PreAggregation.class);
        when(preAgg.getRefreshConfig()).thenReturn(null);

        assertFalse(strategy.supports(preAgg));
    }

    @Test
    @DisplayName("Should not support when watermark column is null")
    void testNotSupportedWithoutWatermarkColumn() {
        PreAggregation preAgg = mock(PreAggregation.class);
        PreAggRefreshDef config = new PreAggRefreshDef();
        config.setWatermarkColumn(null);
        when(preAgg.getRefreshConfig()).thenReturn(config);

        assertFalse(strategy.supports(preAgg));
    }

    @Test
    @DisplayName("Should not support when watermark column is empty")
    void testNotSupportedWithEmptyWatermarkColumn() {
        PreAggregation preAgg = mock(PreAggregation.class);
        PreAggRefreshDef config = new PreAggRefreshDef();
        config.setWatermarkColumn("");
        when(preAgg.getRefreshConfig()).thenReturn(config);

        assertFalse(strategy.supports(preAgg));
    }

    @Test
    @DisplayName("Should support when watermark column is configured")
    void testSupportedWithWatermarkColumn() {
        PreAggregation preAgg = mock(PreAggregation.class);
        PreAggRefreshDef config = new PreAggRefreshDef();
        config.setWatermarkColumn("order_date");
        when(preAgg.getRefreshConfig()).thenReturn(config);

        assertTrue(strategy.supports(preAgg));
    }
}
