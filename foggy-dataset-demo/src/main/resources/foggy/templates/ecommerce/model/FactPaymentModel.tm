/**
 * 支付事实表模型定义
 *
 * @description 电商测试数据 - 支付事实表
 *              包含日期、客户维度关联
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'FactPaymentModel',
    caption: '支付事实表',
    tableName: 'fact_payment',
    idColumn: 'payment_key',

    // 维度定义
    dimensions: [
        {
            name: 'payDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '支付日期',
            description: '支付发生的日期',
            keyDescription: '日期主键，格式yyyyMMdd，如20240101',

            properties: [
                { column: 'year', caption: '年', description: '支付年份' },
                { column: 'quarter', caption: '季度', description: '支付季度（1-4）' },
                { column: 'month', caption: '月', description: '支付月份（1-12）' },
                { column: 'month_name', caption: '月份名称', description: '支付月份中文名' },
                { column: 'day_of_week', caption: '周几', description: '支付在周几（1=周一）' },
                { column: 'is_weekend', caption: '是否周末', description: '支付是否在周末' }
            ]
        },
        {
            name: 'customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_key',
            primaryKey: 'customer_key',
            captionColumn: 'customer_name',
            caption: '客户',
            description: '发起支付的客户',
            keyDescription: '客户代理键，自增整数',

            properties: [
                { column: 'customer_id', caption: '客户ID', description: '客户唯一标识' },
                { column: 'customer_type', caption: '客户类型', description: '客户类型：个人/企业' },
                { column: 'province', caption: '省份', description: '客户所在省份' },
                { column: 'city', caption: '城市', description: '客户所在城市' },
                { column: 'member_level', caption: '会员等级', description: '客户会员等级' }
            ]
        }
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
