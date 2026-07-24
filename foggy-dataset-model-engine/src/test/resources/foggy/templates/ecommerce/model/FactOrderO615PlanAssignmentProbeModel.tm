/**
 * O615 计划占用 RHS 事实探针模型。
 *
 * @description 使用 fact_order 模拟计划明细，并通过 planSheet 维度过滤聚合前数据。
 */
export const model = {
    name: 'FactOrderO615PlanAssignmentProbeModel',
    caption: 'O615 计划占用 RHS 事实探针',
    tableName: 'fact_order',
    idColumn: 'order_key',

    dimensions: [
        {
            name: 'planSheet',
            caption: '计划单探针',
            tableName: 'dim_store',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            properties: [
                { column: 'status', name: 'planStatus', caption: '计划单状态', type: 'STRING' }
            ]
        }
    ],

    properties: [
        { column: 'date_key', name: 'tenantId', caption: '租户ID', type: 'INTEGER' },
        { column: 'store_key', name: 'plannedLoadSiteId', caption: '计划装车站点ID', type: 'INTEGER' },
        { column: 'order_id', name: 'sourceOrderId', caption: '来源运单ID', type: 'STRING' },
        { column: 'order_status', name: 'planAssignmentStatus', caption: '计划明细状态', type: 'STRING' }
    ],

    measures: [
        { column: 'total_quantity', name: 'plannedPieceCount', caption: '计划占用件数', type: 'INTEGER', aggregation: 'sum' }
    ]
};
