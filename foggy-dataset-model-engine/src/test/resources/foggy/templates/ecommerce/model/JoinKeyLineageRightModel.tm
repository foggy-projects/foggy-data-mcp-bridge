/** Deterministic aggregate facts; no persistent fixture tables. */
export const model = {
    name: 'JoinKeyLineageRightModel', idColumn: 'id',
    viewSql: `SELECT 1 AS id, 1 AS tenant_id, 10 AS stock_house_id, 110 AS scope_org_id, 5 AS quantity
        UNION ALL SELECT 2, 1, 11, 111, 6
        UNION ALL SELECT 3, 2, 10, 110, 7`,
    properties: [
        { name: 'id', column: 'id', type: 'INTEGER' },
        { name: 'tenantId', column: 'tenant_id', type: 'INTEGER' },
        { name: 'stockHouseId', column: 'stock_house_id', type: 'INTEGER' },
        { name: 'scopeOrgId', column: 'scope_org_id', type: 'INTEGER' }
    ],
    measures: [{ name: 'quantity', column: 'quantity', type: 'INTEGER', aggregation: 'sum' }]
};
