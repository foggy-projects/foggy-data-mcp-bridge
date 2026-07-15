/**
 * B600 显式运单 join 模型探针。
 *
 * @description 使用 fact_order 模拟 TMS FactExpressOrderModel，包含与库存根模型冲突的
 * tenantId，以及 openingOrg 维度路径。
 */
export const model = {
    name: 'FactOrderB600ExpressOrderProbeModel',
    caption: 'B600 显式运单模型探针',
    tableName: 'fact_order',
    idColumn: 'order_key',

    dimensions: [
        { name: 'tenant', caption: '运单租户', foreignKey: 'date_key' },
        {
            name: 'openingOrg',
            caption: '开单机构',
            tableName: 'dim_store',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            properties: [
                { column: 'store_id', name: 'orgCode', caption: '机构编码', type: 'STRING' }
            ]
        }
    ],

    properties: [
        { column: 'order_id', name: 'expressOrderId', caption: '运单内部ID', type: 'STRING' },
        { column: 'order_id', name: 'orderNo', caption: '运单号', type: 'STRING' },
        { column: 'date_key', name: 'tenantId', caption: '运单租户ID', type: 'INTEGER' }
    ],

    measures: []
};
