/**
 * 带成员权限的销售事实表模型定义（测试用）
 *
 * @description 基于 FactSalesNestedDimModel，在 product 维度上增加 memberPermission 配置
 */
export const model = {
    name: 'FactSalesMemberPermModel',
    caption: '销售事实表（成员权限测试）',
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

            // ---- 内部成员权限（TM 侧） ----
            memberPermission: {
                patch: {
                    visibleColumns: ['id', 'caption', 'productId', 'brand'],
                    forcedSlice: [
                        { field: 'brand', op: '=', value: 'Apple' }
                    ],
                    forcedOrderBy: [
                        { field: 'caption', dir: 'ASC' }
                    ]
                }
            },

            dimensions: [
                {
                    name: 'category',
                    alias: 'productCategory',
                    tableName: 'dim_category_nested',
                    foreignKey: 'category_key',
                    primaryKey: 'category_key',
                    captionColumn: 'category_name',
                    caption: '品类',

                    properties: [
                        { column: 'category_id', caption: '品类ID' },
                        { column: 'category_level', caption: '品类层级' }
                    ],

                    dimensions: [
                        {
                            name: 'group',
                            alias: 'categoryGroup',
                            tableName: 'dim_category_group',
                            foreignKey: 'group_key',
                            primaryKey: 'group_key',
                            captionColumn: 'group_name',
                            caption: '品类组',

                            properties: [
                                { column: 'group_id', caption: '品类组ID' },
                                { column: 'group_type', caption: '组类型' }
                            ]
                        }
                    ]
                }
            ]
        },
        {
            name: 'store',
            tableName: 'dim_store_nested',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            caption: '门店',

            properties: [
                { column: 'store_id', caption: '门店ID' },
                { column: 'store_type', caption: '门店类型' }
            ]
        }
    ],

    measures: [
        { column: 'quantity', caption: '销售数量', type: 'INTEGER', aggregation: 'sum' },
        { column: 'sales_amount', caption: '销售金额', type: 'MONEY', aggregation: 'sum' }
    ]
};
