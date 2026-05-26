/**
 * Invalid for sqlite: formula-only measure has no resolved formula for the current dialect.
 */
export const model = {
    name: 'FactSalesSemanticScaleFormulaUnsupportedDialectInvalidModel',
    caption: '语义缩放公式方言未匹配模型',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    measures: [
        {
            name: 'salesAmountOracleOnlyYuan',
            caption: '销售金额(仅Oracle公式)',
            type: 'MONEY',
            aggregation: 'sum',
            semanticScaleFactor: 100,
            semanticUnit: 'CNY',
            semanticUnitLabel: '元',
            dialectFormulaDef: {
                oracle: {
                    value: 'alias.sales_amount + 0'
                }
            }
        }
    ]
};
