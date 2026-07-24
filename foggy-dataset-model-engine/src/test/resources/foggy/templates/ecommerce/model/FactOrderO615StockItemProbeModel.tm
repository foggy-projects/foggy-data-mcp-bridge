/**
 * O615 库存主侧复现探针模型。
 *
 * @description 使用 fact_order 模拟库存项主表：同时暴露无表 tenant 维度、stockHouse 维度路径和显式字段。
 */
export const model = {
    name: 'FactOrderO615StockItemProbeModel',
    caption: 'O615 库存主侧探针',
    tableName: 'fact_order',
    idColumn: 'order_key',

    dimensions: [
        { name: 'tenant', caption: '租户探针', foreignKey: 'date_key', description: '模拟 TMS tenant 无表维度' },
        {
            name: 'stockHouse',
            caption: '库存仓探针',
            tableName: 'dim_store',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            properties: [
                { column: 'store_id', name: 'srcId', caption: '来源对象ID', type: 'STRING' },
                { column: 'store_type', name: 'useType', caption: '仓库用途类型', type: 'STRING' },
                { column: 'status', caption: '库存仓状态', type: 'STRING' }
            ]
        }
    ],

    properties: [
        { column: 'order_key', caption: '库存项ID', type: 'INTEGER' },
        { column: 'date_key', name: 'tenantId', caption: '租户ID', type: 'INTEGER' },
        { column: 'store_key', name: 'stockHouseId', caption: '库存仓ID', type: 'INTEGER' },
        { column: 'order_id', caption: '内部运单ID', type: 'STRING' },
        { column: 'order_status', caption: '库存状态', type: 'STRING' },
        { column: 'created_at', caption: '创建时间', type: 'DATETIME' }
    ],

    measures: [
        { column: 'total_quantity', name: 'number', caption: '当前库存件数', type: 'INTEGER', aggregation: 'sum' },
        { column: 'total_amount', name: 'stWeight', caption: '当前库存重量', type: 'NUMBER', aggregation: 'sum' }
    ]
};
