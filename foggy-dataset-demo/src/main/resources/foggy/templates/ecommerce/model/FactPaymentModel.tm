/**
 * 支付事实表模型定义
 *
 * @description 电商测试数据 - 支付事实表
 *              包含日期、客户维度关联
 */
import { dicts } from '../dicts.fsscript';
import { buildDateDim, buildCustomerDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactPaymentModel',
    caption: '支付事实表',
    tableName: 'fact_payment',
    idColumn: 'payment_key',

    // 维度定义
    dimensions: [
        buildDateDim({ 
            name: 'payDate', 
            caption: '支付日期', 
            description: '支付发生的日期', 
            contextPrefix: '支付',
            includeProperties: ['year', 'quarter', 'month', 'month_name', 'day_of_week', 'is_weekend']
        }),
        buildCustomerDim({ caption: '客户', description: '发起支付的客户', contextPrefix: '', includeProperties: ['customer_id', 'customer_type', 'province', 'city', 'member_level'] })
    ],

    // 属性定义
    properties: [
        {
            column: 'payment_key',
            caption: '支付代理键',
            type: 'LONG'
        },
        {
            column: 'payment_id',
            caption: '支付业务ID',
            type: 'STRING'
        },
        {
            column: 'order_id',
            caption: '订单ID',
            type: 'STRING'
        },
        {
            column: 'pay_method',
            caption: '支付方式',
            type: 'STRING',
            dictRef: dicts.pay_method
        },
        {
            column: 'pay_channel',
            caption: '支付渠道',
            type: 'STRING',
            dictRef: dicts.pay_channel
        },
        {
            column: 'pay_status',
            caption: '支付状态',
            type: 'STRING',
            dictRef: dicts.pay_status
        },
        {
            column: 'pay_time',
            caption: '支付时间',
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
            column: 'pay_amount',
            caption: '支付金额',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
