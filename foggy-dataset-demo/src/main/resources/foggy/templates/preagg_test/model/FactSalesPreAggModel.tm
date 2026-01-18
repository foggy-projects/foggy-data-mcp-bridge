/**
 * 销售事实表模型（带预聚合配置）
 *
 * @description 用于测试预聚合功能的销售事实表模型
 *              包含多个预聚合配置，测试不同场景
 */
import { dicts } from '../../ecommerce/dicts.fsscript';

export const model = {
    name: 'FactSalesPreAggModel',
    caption: '销售事实表（预聚合测试）',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    // 维度定义
    dimensions: [
        {
            name: 'salesDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '销售日期',
            description: '订单发生的日期',

            properties: [
                { column: 'year', caption: '年' },
                { column: 'quarter', caption: '季度' },
                { column: 'month', caption: '月' },
                { column: 'month_name', caption: '月份名称' },
                { column: 'day_of_week', caption: '周几' },
                { column: 'is_weekend', caption: '是否周末' }
            ]
        },
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: '商品',
            description: '销售的商品信息',

            properties: [
                { column: 'product_id', caption: '商品ID' },
                { column: 'category_id', caption: '一级品类ID' },
                { column: 'category_name', caption: '一级品类名称' },
                { column: 'sub_category_id', caption: '二级品类ID' },
                { column: 'sub_category_name', caption: '二级品类名称' },
                { column: 'brand', caption: '品牌' },
                { column: 'unit_price', caption: '商品售价', type: 'MONEY' },
                { column: 'unit_cost', caption: '商品成本', type: 'MONEY' }
            ]
        },
        {
            name: 'customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_key',
            primaryKey: 'customer_key',
            captionColumn: 'customer_name',
            caption: '客户',
            description: '购买商品的客户信息',

            properties: [
                { column: 'customer_id', caption: '客户ID' },
                { column: 'customer_type', caption: '客户类型' },
                { column: 'gender', caption: '性别' },
                { column: 'age_group', caption: '年龄段' },
                { column: 'province', caption: '省份' },
                { column: 'city', caption: '城市' },
                { column: 'member_level', caption: '会员等级' }
            ]
        },
        {
            name: 'store',
            tableName: 'dim_store',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            caption: '门店',
            description: '销售发生的门店信息',

            properties: [
                { column: 'store_id', caption: '门店ID' },
                { column: 'store_type', caption: '门店类型' },
                { column: 'province', caption: '省份' },
                { column: 'city', caption: '城市' }
            ]
        },
        {
            name: 'channel',
            tableName: 'dim_channel',
            foreignKey: 'channel_key',
            primaryKey: 'channel_key',
            captionColumn: 'channel_name',
            caption: '渠道',
            description: '销售渠道信息',

            properties: [
                { column: 'channel_id', caption: '渠道ID' },
                { column: 'channel_type', caption: '渠道类型' },
                { column: 'platform', caption: '平台' }
            ]
        },
        {
            name: 'promotion',
            tableName: 'dim_promotion',
            foreignKey: 'promotion_key',
            primaryKey: 'promotion_key',
            captionColumn: 'promotion_name',
            caption: '促销活动',
            description: '参与的促销活动信息',

            properties: [
                { column: 'promotion_id', caption: '促销ID' },
                { column: 'promotion_type', caption: '促销类型' },
                { column: 'discount_rate', caption: '折扣率' }
            ]
        }
    ],

    // 属性定义
    properties: [
        { column: 'sales_key', caption: '销售代理键', type: 'LONG' },
        { column: 'order_id', caption: '订单ID', type: 'STRING' },
        { column: 'order_line_no', caption: '订单行号', type: 'INTEGER' },
        { column: 'order_status', caption: '订单状态', type: 'STRING' },
        { column: 'payment_method', caption: '支付方式', type: 'STRING' },
        { column: 'created_at', caption: '创建时间', type: 'DATETIME' }
    ],

    // 度量定义
    measures: [
        { column: 'quantity', name: 'quantity', caption: '销售数量', type: 'INTEGER', aggregation: 'sum' },
        { column: 'unit_price', caption: '单价', type: 'MONEY' },
        { column: 'unit_cost', caption: '单位成本', type: 'MONEY' },
        { column: 'discount_amount', caption: '折扣金额', type: 'MONEY' },
        { column: 'sales_amount', name: 'salesAmount', caption: '销售金额', type: 'MONEY', aggregation: 'sum' },
        { column: 'cost_amount', name: 'costAmount', caption: '成本金额', type: 'MONEY', aggregation: 'sum' },
        { column: 'profit_amount', name: 'profitAmount', caption: '利润金额', type: 'MONEY', aggregation: 'sum' },
        { column: 'tax_amount', name: 'taxAmount', caption: '税额', type: 'MONEY' }
    ],

    // ==================== 预聚合配置 ====================
    preAggregations: [
        {
            // 预聚合1：日+商品汇总（高优先级，支持混合查询）
            name: 'daily_product_sales',
            caption: '按日期+商品汇总',
            tableName: 'preagg_daily_product_sales',
            priority: 80,

            // 包含的维度
            dimensions: ['salesDate', 'product'],

            // 时间维度粒度
            granularity: {
                salesDate: 'day'
            },

            // 包含的维度属性
            dimensionProperties: {
                product: ['category_name', 'brand']
            },

            // 包含的度量及聚合方式
            measures: [
                { name: 'quantity', aggregation: 'SUM', columnName: 'quantity_sum' },
                { name: 'salesAmount', aggregation: 'SUM', columnName: 'sales_amount_sum' },
                { name: 'costAmount', aggregation: 'SUM', columnName: 'cost_amount_sum' },
                { name: 'profitAmount', aggregation: 'SUM', columnName: 'profit_amount_sum' },
                { name: 'orderCount', aggregation: 'COUNT', columnName: 'order_count' }
            ],

            // 刷新配置（增量刷新，支持混合查询）
            refresh: {
                strategy: 'INCREMENTAL',
                schedule: '0 2 * * *',
                watermarkColumn: 'salesDate$id',
                lookbackDays: 3
            },

            enabled: true
        },
        {
            // 预聚合2：月+品类汇总（中优先级，粗粒度）
            name: 'monthly_category_sales',
            caption: '按月+品类汇总',
            tableName: 'preagg_monthly_category_sales',
            priority: 60,

            dimensions: ['salesDate', 'product'],

            granularity: {
                salesDate: 'month'
            },

            dimensionProperties: {
                product: ['category_name']
            },

            measures: [
                { name: 'quantity', aggregation: 'SUM', columnName: 'quantity_sum' },
                { name: 'salesAmount', aggregation: 'SUM', columnName: 'sales_amount_sum' },
                { name: 'costAmount', aggregation: 'SUM', columnName: 'cost_amount_sum' },
                { name: 'profitAmount', aggregation: 'SUM', columnName: 'profit_amount_sum' },
                { name: 'orderCount', aggregation: 'COUNT', columnName: 'order_count' }
            ],

            refresh: {
                strategy: 'FULL',
                schedule: '0 3 1 * *'
            },

            enabled: true
        },
        {
            // 预聚合3：日+客户+渠道汇总
            name: 'daily_customer_channel_sales',
            caption: '按日期+客户+渠道汇总',
            tableName: 'preagg_daily_customer_channel_sales',
            priority: 70,

            dimensions: ['salesDate', 'customer', 'channel'],

            granularity: {
                salesDate: 'day'
            },

            dimensionProperties: {
                customer: ['province', 'city'],
                channel: ['channel_type']
            },

            measures: [
                { name: 'quantity', aggregation: 'SUM', columnName: 'quantity_sum' },
                { name: 'salesAmount', aggregation: 'SUM', columnName: 'sales_amount_sum' },
                { name: 'orderCount', aggregation: 'COUNT', columnName: 'order_count' }
            ],

            refresh: {
                strategy: 'INCREMENTAL',
                schedule: '0 2 * * *',
                watermarkColumn: 'salesDate$id',
                lookbackDays: 3
            },

            enabled: true
        }
    ]
};
