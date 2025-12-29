/**
 * 带嵌套维度的销售事实表模型定义
 *
 * @description 电商测试数据 - 演示嵌套维度（雪花模型）
 *              事实表 -> 产品维度 -> 品类维度 -> 品类组维度
 */
export const model = {
    name: 'FactSalesNestedDimModel',
    caption: '销售事实表（嵌套维度）',
    tableName: 'fact_sales_nested',
    idColumn: 'sales_key',

    // 维度定义 - 使用嵌套维度形成雪花结构
    dimensions: [
        {
            name: 'salesDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '销售日期',

            properties: [
                { column: 'year', caption: '年' },
                { column: 'quarter', caption: '季度' },
                { column: 'month', caption: '月' }
            ]
        },
        {
            // 一级维度：产品
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

            // 嵌套子维度：品类（从产品表的 category_key 关联）
            dimensions: [
                {
                    name: 'category',
                    alias: 'productCategory',  // 别名，简化访问
                    tableName: 'dim_category_nested',
                    foreignKey: 'category_key', // 在 dim_product_nested 表上的外键
                    primaryKey: 'category_key',
                    captionColumn: 'category_name',
                    caption: '品类',

                    properties: [
                        { column: 'category_id', caption: '品类ID' },
                        { column: 'category_level', caption: '品类层级' }
                    ],

                    // 继续嵌套：品类组（从品类表的 group_key 关联）
                    dimensions: [
                        {
                            name: 'group',
                            alias: 'categoryGroup',  // 别名，简化访问
                            tableName: 'dim_category_group',
                            foreignKey: 'group_key',  // 在 dim_category 表上的外键
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
            // 一级维度：门店
            name: 'store',
            tableName: 'dim_store_nested',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            caption: '门店',

            properties: [
                { column: 'store_id', caption: '门店ID' },
                { column: 'store_type', caption: '门店类型' }
            ],

            // 嵌套子维度：区域
            dimensions: [
                {
                    name: 'region',
                    alias: 'storeRegion',  // 别名，简化访问
                    tableName: 'dim_region_nested',
                    foreignKey: 'region_key',  // 在 dim_store_nested 表上的外键
                    primaryKey: 'region_key',
                    captionColumn: 'region_name',
                    caption: '区域',

                    properties: [
                        { column: 'region_id', caption: '区域ID' },
                        { column: 'province', caption: '省份' },
                        { column: 'city', caption: '城市' }
                    ]
                }
            ]
        }
    ],

    // 度量定义
    measures: [
        {
            column: 'quantity',
            caption: '销售数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'sales_amount',
            caption: '销售金额',
            type: 'MONEY',
            aggregation: 'sum'
        },
        {
            column: 'cost_amount',
            caption: '成本金额',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
