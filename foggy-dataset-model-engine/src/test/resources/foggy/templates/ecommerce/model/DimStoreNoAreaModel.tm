/**
 * 门店维度测试模型。
 *
 * @description 用于构造显式 join 与 aggregate relation 输出同名字段不冲突的测试场景。
 */
export const model = {
    name: 'DimStoreNoAreaModel',
    caption: '门店维度无面积测试模型',
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
            column: 'store_type',
            caption: '门店类型',
            type: 'STRING'
        },
        {
            column: 'status',
            caption: '状态',
            type: 'STRING'
        }
    ],

    measures: []
};
