/**
 * semanticScaleFactor with formulaDef / dialectFormulaDef.
 */
export const model = {
    name: 'FactSalesSemanticScaleFormulaModel',
    caption: '语义缩放公式模型',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    properties: [
        {
            column: 'order_id',
            name: 'orderId',
            caption: '订单ID',
            type: 'STRING'
        },
        {
            column: 'sales_amount',
            name: 'salesAmountFormulaLeafYuan',
            caption: '销售金额(value公式属性)',
            type: 'MONEY',
            semanticScaleFactor: 100,
            semanticUnit: 'CNY',
            semanticUnitLabel: '元',
            formulaDef: {
                value: 'alias.sales_amount + 2'
            }
        }
    ],

    measures: [
        {
            name: 'salesAmountFormulaYuan',
            caption: '销售金额(value公式)',
            type: 'MONEY',
            aggregation: 'sum',
            semanticScaleFactor: 100,
            semanticUnit: 'CNY',
            semanticUnitLabel: '元',
            formulaDef: {
                value: 'alias.sales_amount + 0'
            }
        },
        {
            name: 'salesAmountBuilderYuan',
            caption: '销售金额(builder公式)',
            type: 'MONEY',
            aggregation: 'sum',
            semanticScaleFactor: 100,
            semanticUnit: 'CNY',
            semanticUnitLabel: '元',
            formulaDef: {
                builder: (alias) => {
                    return `${alias}.sales_amount + 1`;
                }
            }
        },
        {
            name: 'taxDialectFormulaYuan',
            caption: '税额(方言value公式)',
            type: 'MONEY',
            aggregation: 'sum',
            semanticScaleFactor: 100,
            semanticUnit: 'CNY',
            semanticUnitLabel: '元',
            dialectFormulaDef: {
                sqlite: {
                    value: 'alias.tax_amount + 0'
                },
                postgresql: {
                    value: 'alias.tax_amount + 0'
                },
                mysql: {
                    value: 'alias.tax_amount + 0'
                }
            }
        }
    ]
};
