package com.foggyframework.dataset.model.def.preagg;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 预聚合定义
 * <p>
 * 在 TM 文件中配置预聚合表的完整定义，包括：
 * <ul>
 *   <li>包含的维度及其粒度</li>
 *   <li>包含的度量及聚合方式</li>
 *   <li>刷新策略配置</li>
 *   <li>永久过滤条件</li>
 * </ul>
 * </p>
 *
 * <h3>TM 文件配置示例</h3>
 * <pre>
 * preAggregations: [
 *     {
 *         name: 'daily_product_sales',
 *         caption: '按产品日销售汇总',
 *         tableName: 'preagg_daily_product_sales',
 *         priority: 80,
 *         dimensions: ['salesDate', 'product'],
         *         granularity: { salesDate: 'day' },
         *         dimensionProperties: {
 *             salesDate: ['caption', 'year', 'quarter', 'month'],
 *             product: ['id', 'categoryName', 'brand']
 *         },
 *         dimensionPropertyColumnNames: {
 *             salesDate: { caption: 'full_date', year: 'year', quarter: 'quarter', month: 'month' },
 *             product: { id: 'product_key', categoryName: 'category_name', brand: 'brand' }
 *         },
 *         measures: [
 *             { name: 'quantity', aggregation: 'SUM' },
 *             { name: 'salesAmount', aggregation: 'SUM' }
 *         ],
 *         refresh: {
 *             strategy: 'INCREMENTAL',
 *             schedule: '0 2 * * *',
 *             watermarkColumn: 'salesDate$caption',
 *             lookbackDays: 3
 *         },
 *         enabled: true
 *     }
 * ]
 * </pre>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
public class PreAggregationDef {

    /**
     * 预聚合名称（在模型内唯一）
     */
    private String name;

    /**
     * 显示名称
     */
    private String caption;

    /**
     * 预聚合表名
     */
    private String tableName;

    /**
     * 数据库 schema（可选，默认使用主表的 schema）
     */
    private String schema;

    /**
     * 优先级（1-100，越高越优先匹配）
     * <p>
     * 当多个预聚合都能满足查询需求时，优先使用高优先级的预聚合。
     * 借鉴 Looker aggregate awareness 的设计。
     * </p>
     */
    private int priority = 50;

    /**
     * 包含的维度名称列表
     * <p>
     * 引用 TM 模型中定义的维度名称。
     * </p>
     */
    private List<String> dimensions;

    /**
     * 时间维度粒度配置
     * <p>
     * key: 维度名称
     * value: 粒度（minute, hour, day, week, month, quarter, year）
     * </p>
     */
    private Map<String, String> granularity;

    /**
     * 维度属性配置
     * <p>
     * key: 维度名称
     * value: 包含的属性名称列表
     * </p>
     */
    private Map<String, List<String>> dimensionProperties;

    /**
     * 维度属性在预聚合表中的列名映射（可选）
     * <p>
     * 如果不配置，系统将使用命名约定推断列名：
     * <ul>
     *   <li>caption: 时间维度用 full_date，其他维度用 dimName_name</li>
     *   <li>id: 用 dimName_key</li>
     *   <li>其他属性: 用 snake_case 转换</li>
     * </ul>
     * 配置格式: { dimName: { propName: columnName } }
     * 例如: { product: { caption: 'product_name', categoryName: 'category_name' } }
     * </p>
     */
    private Map<String, Map<String, String>> dimensionPropertyColumnNames;

    /**
     * 包含的度量定义列表
     */
    private List<PreAggMeasureDef> measures;

    /**
     * 刷新配置
     */
    private PreAggRefreshDef refresh;

    /**
     * 永久过滤条件
     * <p>
     * 只有满足这些条件的数据才会被聚合。
     * 查询时如果过滤条件与预聚合的过滤条件兼容，才能使用该预聚合。
     * </p>
     */
    private List<PreAggFilterDef> filters;

    /**
     * 是否启用
     */
    private boolean enabled = true;
}
