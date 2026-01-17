package com.foggyframework.dataset.db.model.preagg.refresh;

import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;

import javax.sql.DataSource;

/**
 * 预聚合刷新策略接口
 * <p>
 * 定义预聚合表的刷新逻辑。不同的刷新策略（全量、增量）实现此接口。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
public interface PreAggRefreshStrategy {

    /**
     * 获取策略名称
     *
     * @return 策略名称（FULL / INCREMENTAL）
     */
    String getStrategyName();

    /**
     * 执行刷新
     *
     * @param preAgg      预聚合配置
     * @param sourceModel 源模型（事实表）
     * @param dataSource  数据源
     * @param context     刷新上下文
     * @return 刷新结果
     */
    PreAggRefreshResult refresh(PreAggregation preAgg,
                                 TableModel sourceModel,
                                 DataSource dataSource,
                                 PreAggRefreshContext context);

    /**
     * 是否支持该预聚合的刷新
     *
     * @param preAgg 预聚合配置
     * @return 是否支持
     */
    boolean supports(PreAggregation preAgg);
}
