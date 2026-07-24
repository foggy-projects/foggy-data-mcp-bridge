/**
 * Sales fact table model focused on aggregate relation string aggregation tests.
 *
 * @description Minimal fact_sales model that declares a GROUP_CONCAT measure.
 */
export const model = {
    name: 'FactSalesStringAggModel',
    caption: '销售事实表字符串聚合探针',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    properties: [
        {
            column: 'sales_key',
            name: 'salesKey',
            caption: '销售代理键',
            type: 'LONG'
        },
        {
            column: 'order_id',
            name: 'orderId',
            caption: '订单ID',
            type: 'STRING'
        },
        {
            column: 'order_status',
            name: 'orderStatus',
            caption: '订单状态',
            type: 'STRING'
        },
        {
            column: 'payment_method',
            name: 'paymentMethod',
            caption: '支付方式',
            type: 'STRING'
        }
    ],

    measures: [
        {
            column: 'sales_amount',
            name: 'salesAmount',
            caption: '销售金额',
            type: 'MONEY'
        },
        {
            column: 'customer_key',
            name: 'uniqueCustomers',
            caption: '独立客户数',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        },
        {
            column: 'payment_method',
            name: 'paymentMethodList',
            caption: '支付方式列表',
            type: 'STRING',
            aggregation: 'GROUP_CONCAT'
        }
    ]
};
