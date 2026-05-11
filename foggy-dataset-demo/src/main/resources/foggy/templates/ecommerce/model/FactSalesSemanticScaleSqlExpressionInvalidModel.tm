/**
 * Invalid: column must remain a physical column name when semanticScaleFactor is used.
 */
export const model = {
    name: 'FactSalesSemanticScaleSqlExpressionInvalidModel',
    caption: '非法语义缩放 SQL 表达式模型',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    measures: [
        {
            column: 'sales_amount / 100.0',
            name: 'salesAmountYuan',
            caption: '销售金额',
            type: 'MONEY',
            semanticScaleFactor: 100
        }
    ]
};
