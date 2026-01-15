/**
 * 订单事实表模型定义
 *
 * @description 电商测试数据 - 订单事实表（订单头）
 *              包含日期、客户、门店、渠道、促销等维度关联
 */
import { dicts } from '../dicts.fsscript';
import { buildDateDim, buildCustomerDim, buildStoreDim, buildChannelDim, buildPromotionDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactOrderModel',
    caption: '订单事实表',
    tableName: 'fact_order',
    idColumn: 'order_key',

    // 维度定义 - 关联维度表
    dimensions: [
        buildDateDim({ 
            name: 'orderDate', 
            caption: '订单日期', 
            description: '订单创建的日期', 
            contextPrefix: '下单',
            includeProperties: ['year', 'quarter', 'month', 'month_name', 'week_of_year', 'day_of_week', 'day_name', 'is_weekend', 'is_holiday']
        }),
        buildCustomerDim({ caption: '客户', description: '下单客户信息', contextPrefix: '下单客户' }),
        buildStoreDim({ caption: '门店', description: '订单归属门店', contextPrefix: '订单', includeProperties: ['store_id', 'store_type', 'province', 'city', 'manager_name'] }),
        buildChannelDim({ caption: '渠道', description: '订单来源渠道', contextPrefix: '订单' }),
        buildPromotionDim({ caption: '促销活动', description: '订单参与的促销活动', contextPrefix: '订单' })
    ],

    // 属性定义 - 事实表自身属性
    properties: [
        {
            column: 'order_key',
            caption: '订单代理键',
            type: 'LONG'
        },
        {
            column: 'order_id',
            caption: '订单ID',
            type: 'STRING'
        },
        {
            column: 'order_status',
            caption: '订单状态',
            type: 'STRING',
            dictRef: dicts.order_status
        },
        {
            column: 'payment_status',
            caption: '支付状态',
            type: 'STRING',
            dictRef: dicts.payment_status
        },
        {
            column: 'order_time',
            caption: '下单时间',
            type: 'DATETIME'
        },
        {
            column: 'created_at',
            caption: '创建时间',
            type: 'DATETIME'
        }
    ],

    // 度量定义（不预设聚合方式，需显式使用 sum()、avg() 等函数）
    measures: [
        {
            column: 'total_quantity',
            name: 'quantity',
            caption: '订单总数量',
            type: 'INTEGER'
        },
        {
            column: 'total_amount',
            name: 'amount',
            caption: '订单总额',
            type: 'MONEY'
        },
        {
            column: 'discount_amount',
            name: 'discountAmount',
            caption: '折扣金额',
            type: 'MONEY'
        },
        {
            column: 'freight_amount',
            name: 'freightAmount',
            caption: '运费',
            type: 'MONEY'
        },
        {
            column: 'pay_amount',
            name: 'payAmount',
            caption: '订单应付金额',
            type: 'MONEY'
        }
    ]
};
