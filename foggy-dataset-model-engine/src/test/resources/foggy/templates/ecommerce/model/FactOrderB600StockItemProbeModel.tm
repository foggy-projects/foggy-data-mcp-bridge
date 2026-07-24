/**
 * B600 库存根模型探针。
 *
 * @description 模拟 TMS FactStockItemModel：根表本身有 tenantId，且通过 sourceOrder
 * 维度再关联一次运单表。QM 同时显式 join 运单模型时，ColumnRef 不能退化为根表字段。
 */
export const model = {
    name: 'FactOrderB600StockItemProbeModel',
    caption: 'B600 库存根模型探针',
    tableName: 'fact_order',
    idColumn: 'order_key',

    dimensions: [
        { name: 'tenant', caption: '库存租户', foreignKey: 'date_key' },
        {
            name: 'sourceOrder',
            caption: '来源运单',
            tableName: 'fact_order',
            foreignKey: 'order_id',
            primaryKey: 'order_id',
            captionColumn: 'order_id',
            properties: [
                { column: 'date_key', name: 'tenantId', caption: '来源运单租户', type: 'INTEGER' },
                { column: 'order_id', name: 'orderNo', caption: '来源运单号', type: 'STRING' }
            ]
        }
    ],

    properties: [
        { column: 'order_key', name: 'stockItemId', caption: '库存项ID', type: 'INTEGER' },
        { column: 'order_id', name: 'orderId', caption: '内部运单ID', type: 'STRING' },
        { column: 'date_key', name: 'tenantId', caption: '库存租户ID', type: 'INTEGER' },
        { column: 'store_key', name: 'stockHouseId', caption: '库存仓ID', type: 'INTEGER' }
    ],

    measures: []
};
