/**
 * O615 显式运单复现探针模型。
 *
 * @description 使用 fact_order 模拟 TMS FactExpressOrderModel，被 query model 显式 join 后公开 orderNo。
 */
export const model = {
    name: 'FactOrderO615ExpressOrderProbeModel',
    caption: 'O615 显式运单探针',
    tableName: 'fact_order',
    idColumn: 'order_key',

    dimensions: [
        { name: 'tenant', caption: '租户探针', foreignKey: 'date_key', description: '模拟运单 tenant 无表维度' },
        {
            name: 'destinationServiceArea',
            caption: '目的服务区探针',
            tableName: 'dim_store',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            properties: [
                { column: 'store_id', name: 'serviceAreaId', caption: '服务区ID', type: 'STRING' },
                { column: 'status', caption: '服务区状态', type: 'STRING' }
            ]
        }
    ],

    properties: [
        { column: 'order_id', name: 'expressOrderId', caption: '运单内部ID', type: 'STRING' },
        { column: 'order_id', name: 'orderNo', caption: '业务运单号', type: 'STRING' },
        { column: 'date_key', name: 'tenantId', caption: '租户ID', type: 'INTEGER' },
        { column: 'order_status', caption: '运单状态', type: 'STRING' }
    ],

    measures: [
        { column: 'total_quantity', name: 'numberOfPackages', caption: '运单件数', type: 'INTEGER', aggregation: 'sum' }
    ]
};
