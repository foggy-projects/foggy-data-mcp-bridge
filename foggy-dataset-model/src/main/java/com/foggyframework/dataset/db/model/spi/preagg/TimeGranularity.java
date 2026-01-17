package com.foggyframework.dataset.db.model.spi.preagg;

/**
 * 时间粒度枚举
 * <p>
 * 定义预聚合支持的时间粒度级别，用于判断查询是否可以使用预聚合表。
 * </p>
 * <p>
 * 粒度从细到粗排列：MINUTE &lt; HOUR &lt; DAY &lt; WEEK &lt; MONTH &lt; QUARTER &lt; YEAR
 * </p>
 * <p>
 * 查询粒度必须 &gt;= 预聚合粒度才能使用该预聚合（可以向上聚合）。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
public enum TimeGranularity {

    /**
     * 分钟级别
     */
    MINUTE(1, "minute"),

    /**
     * 小时级别
     */
    HOUR(60, "hour"),

    /**
     * 天级别
     */
    DAY(1440, "day"),

    /**
     * 周级别
     */
    WEEK(10080, "week"),

    /**
     * 月级别
     */
    MONTH(43200, "month"),

    /**
     * 季度级别
     */
    QUARTER(129600, "quarter"),

    /**
     * 年级别
     */
    YEAR(525600, "year");

    /**
     * 分钟数（用于粒度比较）
     */
    private final int minuteMultiplier;

    /**
     * 配置文件中使用的名称
     */
    private final String configName;

    TimeGranularity(int minuteMultiplier, String configName) {
        this.minuteMultiplier = minuteMultiplier;
        this.configName = configName;
    }

    /**
     * 获取分钟数
     */
    public int getMinuteMultiplier() {
        return minuteMultiplier;
    }

    /**
     * 获取配置名称
     */
    public String getConfigName() {
        return configName;
    }

    /**
     * 获取粒度级别（0 最细，6 最粗）
     */
    public int getLevel() {
        return this.ordinal();
    }

    /**
     * 判断是否可以向上聚合到目标粒度
     * <p>
     * 只有当前粒度 &lt;= 目标粒度时才能聚合。
     * 例如：DAY 可以聚合到 WEEK/MONTH/YEAR，但不能聚合到 HOUR。
     * </p>
     *
     * @param target 目标粒度
     * @return 是否可以聚合
     */
    public boolean canRollupTo(TimeGranularity target) {
        return this.minuteMultiplier <= target.minuteMultiplier;
    }

    /**
     * 判断是否比目标粒度更细
     *
     * @param target 目标粒度
     * @return 是否更细
     */
    public boolean isFinerThan(TimeGranularity target) {
        return this.minuteMultiplier < target.minuteMultiplier;
    }

    /**
     * 判断是否比目标粒度更粗
     *
     * @param target 目标粒度
     * @return 是否更粗
     */
    public boolean isCoarserThan(TimeGranularity target) {
        return this.minuteMultiplier > target.minuteMultiplier;
    }

    /**
     * 从配置名称解析粒度
     *
     * @param configName 配置名称（不区分大小写）
     * @return 粒度枚举，如果无法解析返回 null
     */
    public static TimeGranularity fromConfigName(String configName) {
        if (configName == null || configName.isEmpty()) {
            return null;
        }
        String lowerName = configName.toLowerCase().trim();
        for (TimeGranularity g : values()) {
            if (g.configName.equals(lowerName)) {
                return g;
            }
        }
        // 兼容大写形式
        try {
            return valueOf(configName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
