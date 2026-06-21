/**
 * 成员权限隐藏字段测试模型。
 *
 * @description product 维度使用 status 作为内部 forcedSlice 字段，但不把 status 声明为维度属性。
 */
export const model = {
    name: 'FactSalesHiddenMemberPermModel',
    caption: '销售事实表（成员权限隐藏字段测试）',
    tableName: 'fact_sales_nested',
    idColumn: 'sales_key',

    dimensions: [
        {
            name: 'product',
            tableName: 'dim_product_nested',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: '商品',

            properties: [
                { column: 'product_id', caption: '商品ID' },
                { column: 'brand', caption: '品牌' },
                { column: 'unit_price', caption: '商品售价', type: 'MONEY' }
            ],

            memberPermission: {
                patch: {
                    forcedSlice: [
                        { field: 'status', op: '=', value: 'ACTIVE' }
                    ],
                    forcedOrderBy: [
                        { field: 'caption', dir: 'ASC' }
                    ]
                }
            }
        }
    ],

    measures: [
        { column: 'quantity', caption: '销售数量', type: 'INTEGER', aggregation: 'sum' },
        { column: 'sales_amount', caption: '销售金额', type: 'MONEY', aggregation: 'sum' }
    ]
};
