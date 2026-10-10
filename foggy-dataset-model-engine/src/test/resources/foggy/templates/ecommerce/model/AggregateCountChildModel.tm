export const model = {
    name: 'AggregateCountChildModel', tableName: 'aggregate_count_child', idColumn: 'id',
    properties: [
        { name: 'tenantId', column: 'tenant_id', type: 'INTEGER' },
        { name: 'orderId', column: 'order_id', type: 'INTEGER' },
        { name: 'valid', column: 'valid', type: 'INTEGER' },
        { name: 'label', column: 'label', type: 'STRING' },
        { name: 'amount', column: 'amount', type: 'NUMBER' }
    ]
};
