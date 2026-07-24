package com.foggyframework.dataset.model.preagg.refresh;

import com.foggyframework.dataset.db.dialect.FDialect;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 预聚合刷新上下文
 * <p>
 * 包含刷新过程中需要的所有上下文信息。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
public class PreAggRefreshContext {

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 预聚合名称
     */
    private String preAggName;

    /**
     * 刷新开始时间
     */
    private LocalDateTime startTime;

    /**
     * 上次刷新时间（用于增量刷新）
     */
    private LocalDateTime lastRefreshTime;

    /**
     * 上次水位线值（用于增量刷新）
     */
    private Object lastWatermark;

    /**
     * 是否强制全量刷新
     */
    private boolean forceFullRefresh;

    /**
     * 数据库方言（用于生成方言特定的 SQL）
     */
    private FDialect dialect;

    /**
     * 扩展数据
     */
    private Map<String, Object> extData = new HashMap<>();

    /**
     * 创建上下文
     */
    public static PreAggRefreshContext of(String modelName, String preAggName) {
        PreAggRefreshContext context = new PreAggRefreshContext();
        context.setModelName(modelName);
        context.setPreAggName(preAggName);
        context.setStartTime(LocalDateTime.now());
        return context;
    }
}
