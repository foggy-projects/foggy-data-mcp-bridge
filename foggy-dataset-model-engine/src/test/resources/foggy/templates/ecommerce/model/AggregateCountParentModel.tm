export const model = {
    name: 'AggregateCountParentModel', tableName: 'aggregate_count_parent', idColumn: 'id',
    properties: [
        { name: 'tenantId', column: 'tenant_id', type: 'INTEGER' },
        { name: 'orderId', column: 'order_id', type: 'INTEGER' },
        { name: 'source', column: 'source', type: 'STRING' }
    ],
    measures: [{ name: 'parentCount', column: 'id', type: 'BIGINT', aggregation: 'COUNT' }]
};
