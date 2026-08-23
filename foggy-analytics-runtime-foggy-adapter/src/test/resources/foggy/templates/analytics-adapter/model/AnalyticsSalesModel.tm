export const model = {
    name: 'AnalyticsSalesModel',
    caption: 'Analytics adapter sales',
    tableName: 'analytics_sales',
    idColumn: 'sale_id',
    properties: [
        {
            column: 'sale_id',
            name: 'saleId',
            caption: 'Sale ID',
            type: 'INTEGER'
        },
        {
            column: 'region',
            caption: 'Region',
            type: 'STRING'
        }
    ],
    measures: [
        {
            column: 'amount',
            caption: 'Amount',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
