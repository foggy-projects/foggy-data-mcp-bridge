/**
 * Deterministic 9.3.4 Step 3 pre-aggregation model.
 *
 * This model is intentionally isolated from the historical preagg_test
 * fixtures, whose MySQL and SQLite physical contracts differ.
 */
export const model = {
    name: 'V934PivotPreAggModel',
    caption: 'V934 Pivot PreAgg Fixture',
    tableName: 'v934_preagg_fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        {
            name: 'salesDate',
            tableName: 'v934_preagg_dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            timeRole: 'business_date',
            properties: []
        },
        {
            name: 'product',
            tableName: 'v934_preagg_dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            properties: [
                { column: 'category_name', caption: 'Category' }
            ]
        }
    ],

    measures: [
        {
            column: 'sales_amount',
            name: 'salesAmount',
            caption: 'Sales amount',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ],

    preAggregations: [
        {
            name: 'v934_daily_product_sales',
            caption: 'V934 daily product sales',
            tableName: 'v934_preagg_daily_product_sales',
            priority: 100,
            dimensions: ['salesDate', 'product'],
            granularity: {
                salesDate: 'day'
            },
            dimensionProperties: {
                salesDate: ['id'],
                product: ['id', 'categoryName']
            },
            dimensionPropertyColumnNames: {
                salesDate: {
                    id: 'date_key'
                },
                product: {
                    id: 'product_key',
                    categoryName: 'category_name'
                }
            },
            measures: [
                {
                    name: 'salesAmount',
                    aggregation: 'SUM',
                    columnName: 'sales_amount_sum'
                }
            ],
            refresh: {
                strategy: 'FULL'
            },
            enabled: true
        }
    ]
};
