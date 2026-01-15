/**
 * 退货事实表模型定义
 *
 * @description 电商测试数据 - 退货事实表
 *              包含日期、商品、客户、门店维度关联
 */
import { dicts } from '../dicts.fsscript';
import { buildDateDim, buildProductDim, buildCustomerDim, buildStoreDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactReturnModel',
    caption: '退货事实表',
    tableName: 'fact_return',
    idColumn: 'return_key',

    // 维度定义
    dimensions: [
        buildDateDim({ 
            name: 'returnDate', 
            caption: '退货日期', 
            description: '退货申请的日期', 
            contextPrefix: '退货',
            includeProperties: ['year', 'quarter', 'month', 'month_name', 'day_of_week']
        }),
        buildProductDim({ caption: '商品', description: '退货商品信息', contextPrefix: '退货商品', includeProperties: ['product_id', 'category_name', 'brand'] }),
        buildCustomerDim({ caption: '客户', description: '申请退货的客户', contextPrefix: '', includeProperties: ['customer_id', 'customer_type', 'province', 'city'] }),
        buildStoreDim({ caption: '门店', description: '处理退货的门店', contextPrefix: '' })
    ],

    // 属性定义
    properties: [
        {
            column: 'return_key',
            caption: '退货代理键',
            type: 'LONG'
        },
        {
            column: 'return_id',
            caption: '退货业务ID',
            type: 'STRING'
        },
        {
            column: 'order_id',
            caption: '原订单ID',
            type: 'STRING'
        },
        {
            column: 'order_line_no',
            caption: '原订单行号',
            type: 'INTEGER'
        },
        {
            column: 'return_reason',
            caption: '退货原因',
            type: 'STRING',
            dictRef: dicts.return_reason
        },
        {
            column: 'return_type',
            caption: '退货类型',
            type: 'STRING',
            dictRef: dicts.return_type
        },
        {
            column: 'return_status',
            caption: '退货状态',
            type: 'STRING',
            dictRef: dicts.return_status
        },
        {
            column: 'return_time',
            caption: '退货时间',
            type: 'DATETIME'
        },
        {
            column: 'created_at',
            caption: '创建时间',
            type: 'DATETIME'
        }
    ],

    // 度量定义
    measures: [
        {
            column: 'return_quantity',
            caption: '退货数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'return_amount',
            caption: '退款金额',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
