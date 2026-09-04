/**
 * DataViewer field-extension propagation test model.
 */
export const model = {
    name: 'FactSalesViewerMetadataModel',
    caption: 'Viewer metadata test model',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    properties: [
        {
            column: 'sales_key',
            name: 'salesKey',
            caption: 'Sales key',
            type: 'INTEGER'
        }
    ],

    measures: [
        {
            column: 'sales_amount',
            name: 'salesAmountMinor',
            caption: 'Sales amount (minor unit)',
            type: 'MONEY',
            extData: {
                viewer: {
                    format: 'money',
                    rawUnit: 'minor',
                    displayUnit: 'CNY',
                    scaleFactor: 100,
                    precision: 2
                },
                internalOnly: 'must-not-reach-frontend'
            }
        }
    ]
};
