/**
 * 订单事实表模型定义
 *
 * @description 电商测试数据 - 订单事实表（订单头）
 *              包含日期、客户、门店、渠道、促销等维度关联
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'FactOrderModel',
    caption: '订单事实表',
    tableName: 'fact_order',
    idColumn: 'order_key',

    // 维度定义 - 关联维度表
    dimensions: [
        {
            name: 'orderDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '订单日期',
            description: '订单创建的日期',
            keyDescription: '日期主键，格式yyyyMMdd，如20240101',

            properties: [
                { column: 'year', caption: '年', description: '下单年份' },
                { column: 'quarter', caption: '季度', description: '下单季度（1-4）' },
                { column: 'month', caption: '月', description: '下单月份（1-12）' },
                { column: 'month_name', caption: '月份名称', description: '下单月份中文名（一月至十二月）' },
                { column: 'week_of_year', caption: '年度周数', description: '下单时是一年中的第几周（1-53）' },
                { column: 'day_of_week', caption: '周几', description: '下单在周几（1=周一）' },
                { column: 'day_name', caption: '星期名称', description: '下单星期中文名（周一至周日）' },
                { column: 'is_weekend', caption: '是否周末', description: '订单是否在周末创建' },
                { column: 'is_holiday', caption: '是否节假日', description: '订单是否在节假日创建' }
            ]
        },
        {
            name: 'customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_key',
            primaryKey: 'customer_key',
            captionColumn: 'customer_name',
            caption: '客户',
            description: '下单客户信息',
            keyDescription: '客户代理键，自增整数',

            properties: [
                { column: 'customer_id', caption: '客户ID', description: '下单客户唯一标识' },
                { column: 'customer_type', caption: '客户类型', description: '下单客户类型：个人/企业' },
                { column: 'gender', caption: '性别', description: '下单客户性别' },
                { column: 'age_group', caption: '年龄段', description: '下单客户年龄段' },
                { column: 'province', caption: '省份', description: '下单客户所在省份' },
                { column: 'city', caption: '城市', description: '下单客户所在城市' },
                { column: 'member_level', caption: '会员等级', description: '下单客户会员等级' }
            ]
        },
        {
            name: 'store',
            tableName: 'dim_store',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            caption: '门店',
            description: '订单归属门店',
            keyDescription: '门店代理键，自增整数',

            properties: [
                { column: 'store_id', caption: '门店ID', description: '门店唯一标识' },
                { column: 'store_type', caption: '门店类型', description: '门店类型：直营店/加盟店/旗舰店' },
                { column: 'province', caption: '省份', description: '门店所在省份' },
                { column: 'city', caption: '城市', description: '门店所在城市' },
                { column: 'manager_name', caption: '店长', description: '门店负责人姓名' }
            ]
        },
        {
            name: 'channel',
            tableName: 'dim_channel',
            foreignKey: 'channel_key',
            primaryKey: 'channel_key',
            captionColumn: 'channel_name',
            caption: '渠道',
            description: '订单来源渠道',
            keyDescription: '渠道代理键，自增整数',

            properties: [
                { column: 'channel_id', caption: '渠道ID', description: '渠道唯一标识' },
                { column: 'channel_type', caption: '渠道类型', description: '渠道类型：线上/线下' },
                { column: 'platform', caption: '平台', description: '订单来源平台：淘宝/京东/线下门店' }
            ]
        },
        {
            name: 'promotion',
            tableName: 'dim_promotion',
            foreignKey: 'promotion_key',
            primaryKey: 'promotion_key',
            captionColumn: 'promotion_name',
            caption: '促销活动',
            description: '订单参与的促销活动',
            keyDescription: '促销活动代理键，自增整数',

            properties: [
                { column: 'promotion_id', caption: '促销ID', description: '促销活动唯一标识' },
                { column: 'promotion_type', caption: '促销类型', description: '促销类型：满减/折扣/赠品' },
                { column: 'discount_rate', caption: '折扣率', description: '促销折扣率' }
            ]
        }
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

    // 度量定义
    measures: [
        {
            column: 'total_quantity',
            caption: '订单总数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'total_amount',
            caption: '订单总额',
            type: 'MONEY',
            aggregation: 'sum'
        },
        {
            column: 'discount_amount',
            caption: '折扣金额',
            type: 'MONEY',
            aggregation: 'sum'
        },
        {
            column: 'freight_amount',
            caption: '运费',
            type: 'MONEY',
            aggregation: 'sum'
        },
        {
            name: 'orderPayAmount',
            column: 'pay_amount',
            caption: '订单应付金额',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
