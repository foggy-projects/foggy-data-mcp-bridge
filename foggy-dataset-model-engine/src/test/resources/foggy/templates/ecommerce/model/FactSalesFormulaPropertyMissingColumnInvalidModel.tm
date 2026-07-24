/**
 * Invalid model for validation diagnostics: formula-backed property misses carrier column.
 */
export const model = {
    name: 'FactSalesFormulaPropertyMissingColumnInvalidModel',
    caption: '公式属性缺少 carrier column 无效模型',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    properties: [
        {
            name: 'salesAmountFormulaLeafYuan',
            caption: '销售金额公式属性',
            type: 'MONEY',
            semanticScaleFactor: 100,
            semanticUnit: 'CNY',
            semanticUnitLabel: '元',
            formulaDef: {
                value: 'alias.sales_amount + 2'
            }
        }
    ]
};
