/**
 * 商品维度模型定义
 *
 * @description 电商测试数据 - 商品维度表
 */
export const model = {
    name: 'DimProductModel',
    caption: '商品维度',
    tableName: 'dim_product',
    idColumn: 'product_key',

    dimensions: [],

    properties: [
        {
            column: 'product_key',
            caption: '商品代理键',
            type: 'INTEGER'
        },
        {
            column: 'product_id',
            caption: '商品业务ID',
            type: 'STRING'
        },
        {
            column: 'product_name',
            caption: '商品名称',
            type: 'STRING'
        },
        {
            column: 'category_id',
            caption: '一级品类ID',
            type: 'STRING'
        },
        {
            column: 'category_name',
            caption: '一级品类名称',
            type: 'STRING'
        },
        {
            column: 'sub_category_id',
            caption: '二级品类ID',
            type: 'STRING'
        },
        {
            column: 'sub_category_name',
            caption: '二级品类名称',
            type: 'STRING'
        },
        {
            column: 'brand',
            caption: '品牌',
            type: 'STRING'
        },
        {
            column: 'supplier_id',
            caption: '供应商ID',
            type: 'STRING'
        },
        {
            column: 'unit_price',
            caption: '售价',
            type: 'MONEY'
        },
        {
            column: 'unit_cost',
            caption: '成本',
            type: 'MONEY'
        },
        {
            column: 'status',
            caption: '状态',
            type: 'STRING'
        },
        {
            column: 'created_at',
            caption: '创建时间',
            type: 'DATETIME'
        },
        {
            column: 'updated_at',
            caption: '更新时间',
            type: 'DATETIME'
        }
    ],

    measures: []
};
