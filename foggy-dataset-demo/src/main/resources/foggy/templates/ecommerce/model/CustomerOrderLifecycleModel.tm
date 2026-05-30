/**
 * 客户订单生命周期派生模型
 *
 * @description Lite customer lifecycle fixture for first-order cohort and 30/60/90 day repurchase analysis.
 */
export const model = {
    name: 'CustomerOrderLifecycleModel',
    caption: '客户订单生命周期',
    tableName: 'customer_order_lifecycle',
    idColumn: 'customer_id',

    properties: [
        {
            column: 'customer_id',
            caption: '客户ID',
            description: '客户唯一标识，与订单客户维度 customerId 对齐。',
            type: 'STRING'
        },
        {
            column: 'customer_name',
            caption: '客户名称',
            type: 'STRING'
        },
        {
            column: 'first_order_date',
            caption: '首购日期',
            description: '客户首次下单日期，用于首购客户 cohort、首购月份和复购窗口分析。',
            type: 'DATE'
        },
        {
            column: 'first_order_month',
            caption: '首购月份',
            description: '客户首次下单月份，格式 YYYY-MM。',
            type: 'STRING'
        },
        {
            column: 'first_order_channel',
            caption: '首购渠道类型',
            description: '客户首次下单时的渠道类型。',
            type: 'STRING'
        }
    ],

    measures: [
        {
            name: 'customerCount',
            caption: '客户数',
            aggregation: 'count',
            type: 'INTEGER'
        },
        {
            column: 'order_count',
            name: 'orderCount',
            caption: '订单数',
            aggregation: 'sum',
            type: 'INTEGER'
        },
        {
            column: 'lifetime_amount',
            name: 'lifetimeAmount',
            caption: '生命周期订单金额',
            aggregation: 'sum',
            type: 'MONEY'
        },
        {
            column: 'repurchase_30d_flag',
            name: 'repurchase30dCustomerCount',
            caption: '30天复购客户数',
            description: '首购后30天内再次下单的客户数；字段为预计算客户级标记，聚合时求和。',
            aggregation: 'sum',
            type: 'INTEGER'
        },
        {
            column: 'repurchase_60d_flag',
            name: 'repurchase60dCustomerCount',
            caption: '60天复购客户数',
            description: '首购后60天内再次下单的客户数；字段为预计算客户级标记，聚合时求和。',
            aggregation: 'sum',
            type: 'INTEGER'
        },
        {
            column: 'repurchase_90d_flag',
            name: 'repurchase90dCustomerCount',
            caption: '90天复购客户数',
            description: '首购后90天内再次下单的客户数；字段为预计算客户级标记，聚合时求和。',
            aggregation: 'sum',
            type: 'INTEGER'
        }
    ]
};
