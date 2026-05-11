/**
 * Invalid: semanticScaleFactor cannot be combined with formulaDef.
 */
export const model = {
    name: 'FactSalesSemanticScaleFormulaInvalidModel',
    caption: '非法语义缩放公式模型',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    measures: [
        {
            column: 'sales_amount',
            name: 'salesAmountYuan',
            caption: '销售金额',
            type: 'MONEY',
            semanticScaleFactor: 100,
            formulaDef: {
                builder: (alias) => {
                    return `${alias}.sales_amount + 1`;
                }
            }
        }
    ]
};
