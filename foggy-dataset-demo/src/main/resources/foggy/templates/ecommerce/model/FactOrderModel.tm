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
            description: '业务订单号，可与 CrmLead.convertedOrderId 对齐；用于受治理跨模型漏斗关系评估',
            type: 'STRING'
        },
        {
            column: 'order_status',
            caption: '订单状态',
            description: '订单生命周期状态。打开订单通常指待处理、已确认或处理中，不包括已发货、已完成、已取消或已退款。',
            type: 'STRING',
            dictRef: dicts.order_status,
            dictionaryDiscovery: {
                enabled: true,
                strategy: 'group_by',
                maxValues: 20,
                refreshTtlSeconds: 3600,
                exposeToLlm: true,
                sensitive: false,
                aliases: {
                    open_order: {
                        values: ['PENDING', 'CONFIRMED', 'PROCESSING'],
                        description: '打开订单，不包括已发货、已完成、已取消或已退款'
                    }
                }
            }
        },
        {
            column: 'payment_status',
            caption: '支付状态',
            description: '订单支付结算状态，与订单生命周期状态不同；用于描述未支付、部分支付、已支付、退款中或已退款等支付进度。',
            type: 'STRING',
            dictRef: dicts.payment_status,
            dictionaryDiscovery: {
                enabled: true,
                strategy: 'group_by',
                maxValues: 20,
                refreshTtlSeconds: 3600,
                exposeToLlm: true,
                sensitive: false,
                aliases: {
                    not_fully_paid: {
                        values: ['UNPAID', 'PARTIAL'],
                        description: '未完全支付订单'
                    },
                    settled_or_refunded: {
                        values: ['PAID', 'REFUNDED'],
                        description: '已完成支付或已退款订单'
                    }
                }
            }
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
