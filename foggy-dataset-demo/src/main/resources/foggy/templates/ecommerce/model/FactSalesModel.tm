/**
 * 销售事实表模型定义
 *
 * @description 电商测试数据 - 销售事实表（订单明细）
 *              包含日期、商品、客户、门店、渠道、促销等维度关联
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'FactSalesModel',
    caption: '销售事实表',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    // 维度定义 - 关联维度表
    dimensions: [
        {
            name: 'salesDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '销售日期',
            description: '订单发生的日期',
            keyDescription: '日期主键，格式yyyyMMdd，如20240101',

            properties: [
                { column: 'year', caption: '年', description: '销售发生的年份' },
                { column: 'quarter', caption: '季度', description: '销售发生的季度（1-4）' },
                { column: 'month', caption: '月', description: '销售发生的月份（1-12）' },
                { column: 'month_name', caption: '月份名称', description: '销售月份中文名（一月至十二月）' },
                { column: 'day_of_week', caption: '周几', description: '销售发生在周几（1=周一）' },
                { column: 'is_weekend', caption: '是否周末', description: '销售是否发生在周末' }
            ]
        },
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: '商品',
            description: '销售的商品信息',
            keyDescription: '商品代理键，自增整数',

            properties: [
                { column: 'product_id', caption: '商品ID', description: '商品唯一标识' },
                { column: 'category_id', caption: '一级品类ID', description: '商品一级分类编码' },
                { column: 'category_name', caption: '一级品类名称', description: '商品一级分类名称，如电子产品、服装' },
                { column: 'sub_category_id', caption: '二级品类ID', description: '商品二级分类编码' },
                { column: 'sub_category_name', caption: '二级品类名称', description: '商品二级分类名称，如手机、T恤' },
                { column: 'brand', caption: '品牌', description: '商品品牌名称' },
                { column: 'unit_price', caption: '商品售价', description: '商品标准售价', type: 'MONEY' },
                { column: 'unit_cost', caption: '商品成本', description: '商品采购成本', type: 'MONEY' }
            ]
        },
        {
            name: 'customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_key',
            primaryKey: 'customer_key',
            captionColumn: 'customer_name',
            caption: '客户',
            description: '购买商品的客户信息',
            keyDescription: '客户代理键，自增整数',

            properties: [
                { column: 'customer_id', caption: '客户ID', description: '客户唯一标识' },
                { column: 'customer_type', caption: '客户类型', description: '客户类型：个人/企业' },
                { column: 'gender', caption: '性别', description: '客户性别：男/女' },
                { column: 'age_group', caption: '年龄段', description: '客户年龄段：18-25/26-35/36-45/46+' },
                { column: 'province', caption: '省份', description: '客户所在省份' },
                { column: 'city', caption: '城市', description: '客户所在城市' },
                { column: 'member_level', caption: '会员等级', description: '客户会员等级：普通/银卡/金卡/钻石' }
            ]
        },
        {
            name: 'store',
            tableName: 'dim_store',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            caption: '门店',
            description: '销售发生的门店信息',
            keyDescription: '门店代理键，自增整数',

            properties: [
                { column: 'store_id', caption: '门店ID', description: '门店唯一标识' },
                { column: 'store_type', caption: '门店类型', description: '门店类型：直营店/加盟店/旗舰店' },
                { column: 'province', caption: '省份', description: '门店所在省份' },
                { column: 'city', caption: '城市', description: '门店所在城市' }
            ]
        },
        {
            name: 'channel',
            tableName: 'dim_channel',
            foreignKey: 'channel_key',
            primaryKey: 'channel_key',
            captionColumn: 'channel_name',
            caption: '渠道',
            description: '销售渠道信息',
            keyDescription: '渠道代理键，自增整数',

            properties: [
                { column: 'channel_id', caption: '渠道ID', description: '渠道唯一标识' },
                { column: 'channel_type', caption: '渠道类型', description: '渠道类型：线上/线下' },
                { column: 'platform', caption: '平台', description: '销售平台：淘宝/京东/线下门店' }
            ]
        },
        {
            name: 'promotion',
            tableName: 'dim_promotion',
            foreignKey: 'promotion_key',
            primaryKey: 'promotion_key',
            captionColumn: 'promotion_name',
            caption: '促销活动',
            description: '参与的促销活动信息',
            keyDescription: '促销活动代理键，自增整数',

            properties: [
                { column: 'promotion_id', caption: '促销ID', description: '促销活动唯一标识' },
                { column: 'promotion_type', caption: '促销类型', description: '促销类型：满减/折扣/赠品' },
                { column: 'discount_rate', caption: '折扣率', description: '促销折扣率，如0.8表示8折' }
            ]
        }
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
