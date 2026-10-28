/** Formula carrier columns must not alias their physical-column siblings. */
export const model = {
    name: 'JoinKeyLineageLeftModel', idColumn: 'id',
    viewSql: `SELECT 1 AS id, 1 AS tenant_id, 10 AS stock_house_id
        UNION ALL SELECT 2, 1, 11
        UNION ALL SELECT 3, 2, 10`,
    properties: [
        { name: 'id', column: 'id', type: 'INTEGER' },
        { name: 'tenantId', column: 'tenant_id', type: 'INTEGER' },
        { name: 'tenantAlias', column: 'tenant_id', type: 'INTEGER' },
        { name: 'stockHouseId', column: 'stock_house_id', type: 'INTEGER' },
        { name: 'stockHouseAlias', column: 'stock_house_id', type: 'INTEGER' },
        { name: 'scopeOrgId', column: 'stock_house_id', type: 'INTEGER',
            formulaDef: { builder: (alias) => '(' + alias + '.stock_house_id + 100)' } }
    ]
};
