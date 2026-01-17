/**
 * 退货事实表模型（带预聚合配置）
 *
 * @description 用于测试多表JOIN场景的预聚合功能
 */

export const model = {
    name: 'FactReturnPreAggModel',
    caption: '退货事实表（预聚合测试）',
    tableName: 'fact_return',
    idColumn: 'return_key',

    // 维度定义
    dimensions: [
        {
            name: 'returnDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '退货日期',
            description: '退货申请的日期',

            properties: [
                { column: 'year', caption: '年' },
                { column: 'quarter', caption: '季度' },
                { column: 'month', caption: '月' },
                { column: 'month_name', caption: '月份名称' },
                { column: 'day_of_week', caption: '周几' }
            ]
        },
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: '商品',
            description: '退货的商品信息',

            properties: [
                { column: 'product_id', caption: '商品ID' },
                { column: 'category_name', caption: '品类名称' },
                { column: 'brand', caption: '品牌' }
            ]
        },
        {
            name: 'customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_key',
            primaryKey: 'customer_key',
            captionColumn: 'customer_name',
            caption: '客户',
            description: '申请退货的客户',

            properties: [
                { column: 'customer_id', caption: '客户ID' },
                { column: 'customer_type', caption: '客户类型' },
                { column: 'province', caption: '省份' },
                { column: 'city', caption: '城市' }
            ]
        },
        {
            name: 'store',
            tableName: 'dim_store',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            caption: '门店',
            description: '处理退货的门店',

            properties: [
                { column: 'store_id', caption: '门店ID' },
                { column: 'store_type', caption: '门店类型' },
                { column: 'province', caption: '省份' },
                { column: 'city', caption: '城市' }
            ]
        }
    ],

    // 属性定义
    properties: [
        { column: 'return_key', caption: '退货代理键', type: 'LONG' },
        { column: 'return_id', caption: '退货业务ID', type: 'STRING' },
        { column: 'order_id', caption: '原订单ID', type: 'STRING' },
        { column: 'order_line_no', caption: '原订单行号', type: 'INTEGER' },
        { column: 'return_reason', caption: '退货原因', type: 'STRING' },
        { column: 'return_type', caption: '退货类型', type: 'STRING' },
        { column: 'return_status', caption: '退货状态', type: 'STRING' },
        { column: 'return_time', caption: '退货时间', type: 'DATETIME' },
        { column: 'created_at', caption: '创建时间', type: 'DATETIME' }
    ],

    // 度量定义
    measures: [
        { column: 'return_quantity', name: 'returnQuantity', caption: '退货数量', type: 'INTEGER', aggregation: 'sum' },
        { column: 'return_amount', name: 'returnAmount', caption: '退款金额', type: 'MONEY', aggregation: 'sum' }
    ],

    // ==================== 预聚合配置 ====================
    preAggregations: [
        {
            // 退货日汇总预聚合
            name: 'daily_return',
            caption: '按日期+商品退货汇总',
            tableName: 'preagg_daily_return',
            priority: 80,

            dimensions: ['returnDate', 'product'],

            granularity: {
                returnDate: 'day'
            },

            dimensionProperties: {
                product: ['category_name']
            },

            measures: [
                { name: 'returnQuantity', aggregation: 'SUM', columnName: 'return_quantity_sum' },
                { name: 'returnAmount', aggregation: 'SUM', columnName: 'return_amount_sum' },
                { name: 'returnCount', aggregation: 'COUNT', columnName: 'return_count' }
            ],

            refresh: {
                strategy: 'INCREMENTAL',
                schedule: '0 3 * * *',
                watermarkColumn: 'returnDate$id',
                lookbackDays: 3
            },

            enabled: true
        }
    ]
};
