/**
 * 品类聚合源模型定义
 *
 * @description aggregate relation 测试夹具：同一张品类维表作为可聚合源，
 *              用于验证左侧 ON key 来自嵌套维度路径时的 SQL 解析。
 */
export const model = {
    name: 'CategoryAggregateSourceModel',
    caption: '品类聚合源',
    tableName: 'dim_category_nested',
    idColumn: 'category_key',

    dimensions: [],

    properties: [
        { column: 'category_key', caption: '品类代理键', type: 'INTEGER' },
        { column: 'category_id', caption: '品类ID', type: 'STRING' },
        { column: 'status', caption: '状态', type: 'STRING' }
    ],

    measures: [
        {
            column: 'category_level',
            name: 'categoryLevel',
            caption: '品类层级',
            type: 'INTEGER',
            aggregation: 'sum'
        }
    ]
};
