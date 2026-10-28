/** Deterministic row measurements; no persistent fixture tables. */
export const model = {
    name: 'RowFormulaMeasurementModel', idColumn: 'id',
    viewSql: `SELECT 1 AS id, 'A' AS warehouse, 1 AS pieces, 2 AS order_pieces, 12.0 AS order_weight
        UNION ALL SELECT 2, 'A', 1, 4, 20.0
        UNION ALL SELECT 3, 'B', 0, 4, 20.0
        UNION ALL SELECT 4, 'A', 1, 2, NULL`,
    properties: [{ name: 'id', column: 'id', type: 'INTEGER' },
        { name: 'warehouse', column: 'warehouse', type: 'STRING' }],
    measures: [{ name: 'pieces', column: 'pieces', type: 'INTEGER', aggregation: 'sum' },
        { name: 'orderPieces', column: 'order_pieces', type: 'INTEGER', aggregation: 'max' },
        { name: 'orderWeight', column: 'order_weight', type: 'DOUBLE', aggregation: 'max' }]
};
