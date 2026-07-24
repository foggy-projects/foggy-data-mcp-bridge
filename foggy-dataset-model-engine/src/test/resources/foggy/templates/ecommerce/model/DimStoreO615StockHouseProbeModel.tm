/**
 * O615 显式库存仓复现探针模型。
 *
 * @description 使用 dim_store 模拟 TMS FactStockHouseModel，被 query model 显式普通 leftJoin。
 */
export const model = {
    name: 'DimStoreO615StockHouseProbeModel',
    caption: 'O615 显式库存仓探针',
    tableName: 'dim_store',
    idColumn: 'store_key',

    dimensions: [
        { name: 'tenant', caption: '租户探针', foreignKey: 'store_key', description: '模拟库存仓 tenant 无表维度' }
    ],

    properties: [
        { column: 'store_key', name: 'stockHouseId', caption: '库存仓ID', type: 'INTEGER' },
        { column: 'store_key', name: 'tenantId', caption: '租户ID', type: 'INTEGER' },
        { column: 'store_id', name: 'srcId', caption: '来源对象ID', type: 'STRING' },
        { column: 'store_type', name: 'useType', caption: '仓库用途类型', type: 'STRING' },
        { column: 'store_name', name: 'caption', caption: '仓库名称', type: 'STRING' },
        { column: 'status', caption: '状态', type: 'STRING' }
    ],

    measures: []
};
