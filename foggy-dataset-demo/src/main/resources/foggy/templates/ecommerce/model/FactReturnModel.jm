/**
 * 退货事实表模型定义
 *
 * @description 电商测试数据 - 退货事实表
 *              包含日期、商品、客户、门店维度关联
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'FactReturnModel',
    caption: '退货事实表',
    tableName: 'fact_return',
    idColumn: 'return_key',

    // 维度定义
    dimensions: [
        {
            name: 'returnDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '退货日期',
            description: '退货申请的日期',
            keyDescription: '日期主键，格式yyyyMMdd，如20240101',

            properties: [
                { column: 'year', caption: '年', description: '退货年份' },
                { column: 'quarter', caption: '季度', description: '退货季度（1-4）' },
                { column: 'month', caption: '月', description: '退货月份（1-12）' },
                { column: 'month_name', caption: '月份名称', description: '退货月份中文名' },
                { column: 'day_of_week', caption: '周几', description: '退货在周几（1=周一）' }
            ]
        },
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: '商品',
            description: '退货商品信息',
            keyDescription: '商品代理键，自增整数',

            properties: [
                { column: 'product_id', caption: '商品ID', description: '商品唯一标识' },
                { column: 'category_name', caption: '品类名称', description: '商品分类名称' },
                { column: 'brand', caption: '品牌', description: '商品品牌名称' }
            ]
        },
        {
            name: 'customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_key',
            primaryKey: 'customer_key',
            captionColumn: 'customer_name',
            caption: '客户',
            description: '申请退货的客户',
            keyDescription: '客户代理键，自增整数',

            properties: [
                { column: 'customer_id', caption: '客户ID', description: '客户唯一标识' },
                { column: 'customer_type', caption: '客户类型', description: '客户类型：个人/企业' },
                { column: 'province', caption: '省份', description: '客户所在省份' },
                { column: 'city', caption: '城市', description: '客户所在城市' }
            ]
        },
        {
            name: 'store',
            tableName: 'dim_store',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            caption: '门店',
            description: '处理退货的门店',
            keyDescription: '门店代理键，自增整数',

            properties: [
                { column: 'store_id', caption: '门店ID', description: '门店唯一标识' },
                { column: 'store_type', caption: '门店类型', description: '门店类型：直营店/加盟店/旗舰店' },
                { column: 'province', caption: '省份', description: '门店所在省份' },
                { column: 'city', caption: '城市', description: '门店所在城市' }
            ]
        }
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
