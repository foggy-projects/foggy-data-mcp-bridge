/**
 * semanticScaleFactor test model.
 */
export const model = {
    name: 'FactSalesSemanticScaleModel',
    caption: '语义缩放销售模型',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        {
            name: 'salesDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '销售日期',
            timeRole: 'business_date',
            recommendedUse: 'time_axis',
            properties: [
                { column: 'year', caption: '年' },
                { column: 'month', caption: '月' }
            ]
        },
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: '商品',
            properties: [
                {
                    column: 'category_name',
                    name: 'categoryName',
                    caption: '一级品类名称',
                    type: 'STRING'
                },
                {
                    column: 'unit_price',
                    name: 'unitPriceYuan',
                    caption: '商品售价',
                    type: 'MONEY',
                    semanticScaleFactor: 100,
                    semanticUnit: 'CNY',
                    semanticUnitLabel: '元'
                }
            ]
        }
    ],

    properties: [
        {
            column: 'order_id',
            name: 'orderId',
            caption: '订单ID',
            type: 'STRING'
        }
    ],

    measures: [
        {
            column: 'sales_amount',
            name: 'salesAmountYuan',
            caption: '销售金额',
            type: 'MONEY',
            aggregation: 'sum',
            semanticScaleFactor: 100,
            semanticUnit: 'CNY',
            semanticUnitLabel: '元'
        }
    ]
};
