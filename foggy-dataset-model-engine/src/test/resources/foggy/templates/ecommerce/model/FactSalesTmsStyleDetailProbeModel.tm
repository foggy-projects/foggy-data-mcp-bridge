/**
 * TMS-style 明细聚合探针模型。
 *
 * @description 复用 fact_sales 测试表，将站点 key 暴露为简单字段，避免 aggregate relation group key 使用带 `$` 的维度路径 alias。
 */
export const model = {
    name: 'FactSalesTmsStyleDetailProbeModel',
    caption: 'TMS风格销售明细探针',
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
            column: 'store_key',
            name: 'storeKey',
            caption: '站点代理键',
            type: 'LONG'
        },
        {
            column: 'order_status',
            name: 'orderStatus',
            caption: '明细状态',
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
            column: 'quantity',
            name: 'quantity',
            caption: '销售数量',
            type: 'INTEGER',
            aggregation: 'SUM'
        },
        {
            column: 'customer_key',
            name: 'uniqueCustomers',
            caption: '独立客户数',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
