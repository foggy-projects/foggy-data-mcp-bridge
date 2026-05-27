/**
 * 门店聚合源模型定义
 *
 * @description aggregate relation 测试夹具：同一张 dim_store 表作为可聚合源，
 *              用于验证左侧 ON key 来自已 join 维度属性时的 SQL 解析。
 */
export const model = {
    name: 'StoreAggregateSourceModel',
    caption: '门店聚合源',
    tableName: 'dim_store',
    idColumn: 'store_key',

    dimensions: [],

    properties: [
        {
            column: 'store_key',
            caption: '门店代理键',
            type: 'INTEGER'
        },
        {
            column: 'store_id',
            caption: '门店业务ID',
            type: 'STRING'
        },
        {
            column: 'status',
            caption: '状态',
            type: 'STRING'
        }
    ],

    measures: [
        {
            column: 'area_sqm',
            name: 'areaSqm',
            caption: '门店面积',
            type: 'NUMBER',
            aggregation: 'sum'
        }
    ]
};
