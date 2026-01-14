/**
 * 销售事实表模型定义
 *
 * @description 电商测试数据 - 销售事实表（订单明细）
 *              包含日期、商品、客户、门店、渠道、促销等维度关联
 */
import { dicts } from '../dicts.fsscript';
import { buildDateDim, buildCustomerDim, buildProductDim, buildStoreDim, buildChannelDim, buildPromotionDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactSalesModel',
    caption: '销售事实表',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    // 维度定义 - 关联维度表
    dimensions: [
        buildDateDim({ name: 'salesDate', caption: '销售日期', description: '订单发生的日期', contextPrefix: '销售' }),
        buildProductDim({ caption: '商品', description: '销售的商品信息', contextPrefix: '销售' }),
        buildCustomerDim({ caption: '客户', description: '购买商品的客户信息', contextPrefix: '购买' }),
        buildStoreDim({ caption: '门店', description: '销售发生的门店信息', contextPrefix: '销售' }),
        buildChannelDim({ caption: '渠道', description: '销售渠道信息', contextPrefix: '销售' }),
        buildPromotionDim({ caption: '促销活动', description: '参与的促销活动信息' })
    ],

    // 属性定义 - 事实表自身属性
    properties: [
        {
            column: 'sales_key',
            caption: '销售代理键',
            type: 'LONG'
        },
        {
            column: 'order_id',
            caption: '订单ID',
            type: 'STRING'
        },
        {
            column: 'order_line_no',
            caption: '订单行号',
            type: 'INTEGER'
        },
        {
            column: 'order_status',
            caption: '订单状态',
            type: 'STRING',
            dictRef: dicts.order_status
        },
        {
            column: 'payment_method',
            caption: '支付方式',
            type: 'STRING',
            dictRef: dicts.payment_method
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
            column: 'quantity',
            caption: '销售数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'unit_price',
            caption: '单价',
            type: 'MONEY'
        },
        {
            column: 'unit_cost',
            caption: '单位成本',
            type: 'MONEY'
        },
        {
            column: 'discount_amount',
            caption: '折扣金额',
            type: 'MONEY'
        },
        {
            column: 'sales_amount',
            name: 'salesAmount',
            caption: '销售金额',
            type: 'MONEY'
        },
        {
            column: 'cost_amount',
            name: 'costAmount',
            caption: '成本金额',
            type: 'MONEY'
        },
        {
            column: 'profit_amount',
            name: 'profitAmount',
            caption: '利润金额',
            type: 'MONEY'
        },
        {
            column: 'tax_amount',
            name: 'taxAmount',
            caption: '税额',
            type: 'MONEY'
        },
        {
            column: 'tax_amount',
            name: 'taxAmount2',
            caption: '税额*2',
            description: '用于测试计算字段',
            type: 'MONEY',
            "formulaDef": {
                builder: (alias) => {
                    return `${alias}.tax_amount+1`;
                }
            }
        }
    ]
};
