/**
 * 销售订单测试模型
 *
 * @description MongoDB 文档模型 - 用于测试计算字段功能
 */
import '@mcpMongoTemplate';

export const model = {
    name: 'SalesOrderTestModel',
    caption: '销售订单测试',
    tableName: 'sales_order_test',
    idColumn: '_id',
    type: 'mongo',
    mongoTemplate: mcpMongoTemplate,

    properties: [
        {
            column: '_id',
            name: 'id',
            caption: '订单ID',
            type: 'STRING'
        },
        {
            column: 'orderNo',
            name: 'orderNo',
            caption: '订单号',
            type: 'STRING'
        },
        {
            column: 'productName',
            name: 'productName',
            caption: '商品名称',
            type: 'STRING'
        },
        {
            column: 'category',
            name: 'category',
            caption: '商品类别',
            type: 'STRING'
        },
        {
            column: 'price',
            name: 'price',
            caption: '单价',
            type: 'NUMBER'
        },
        {
            column: 'quantity',
            name: 'quantity',
            caption: '数量',
            type: 'INTEGER'
        },
        {
            column: 'discount',
            name: 'discount',
            caption: '折扣率',
            type: 'NUMBER',
            description: '折扣百分比，例如10表示10%折扣'
        },
        {
            column: 'taxRate',
            name: 'taxRate',
            caption: '税率',
            type: 'NUMBER',
            description: '税率百分比，例如13表示13%税率'
        },
        {
            column: 'orderDate',
            name: 'orderDate',
            caption: '订单日期',
            type: 'DATETIME'
        },
        {
            column: 'status',
            name: 'status',
            caption: '订单状态',
            type: 'STRING'
        },
        {
            column: 'customerId',
            name: 'customerId',
            caption: '客户ID',
            type: 'STRING'
        },
        {
            column: 'customerName',
            name: 'customerName',
            caption: '客户名称',
            type: 'STRING'
        }
    ],

    measures: [
        {
            column: 'price',
            name: 'avgPrice',
            caption: '平均单价',
            type: 'NUMBER',
            aggregation: 'avg'
        },
        {
            column: 'quantity',
            name: 'totalQuantity',
            caption: '总数量',
            type: 'INTEGER',
            aggregation: 'sum'
        }
    ]
};
