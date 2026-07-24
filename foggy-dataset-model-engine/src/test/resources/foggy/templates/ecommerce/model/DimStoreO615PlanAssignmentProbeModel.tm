/**
 * O615 计划占用 RHS 聚合复现探针模型。
 *
 * @description 使用 dim_store 模拟计划明细聚合源，提供三键 groupBy 和一个聚合度量。
 */
export const model = {
    name: 'DimStoreO615PlanAssignmentProbeModel',
    caption: 'O615 计划占用 RHS 探针',
    tableName: 'dim_store',
    idColumn: 'store_key',

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
        { column: 'store_key', name: 'tenantId', caption: '租户ID', type: 'INTEGER' },
        { column: 'store_id', name: 'plannedLoadSiteId', caption: '计划装车站点ID', type: 'STRING' },
        { column: 'store_id', name: 'sourceOrderId', caption: '来源运单ID', type: 'STRING' },
        { column: 'status', name: 'planAssignmentStatus', caption: '计划明细状态', type: 'STRING' }
    ],

    measures: [
        { column: 'area_sqm', name: 'plannedPieceCount', caption: '计划占用件数', type: 'NUMBER', aggregation: 'sum' }
    ]
};
