/**
 * 库存快照事实表模型定义
 *
 * @description 电商测试数据 - 库存快照事实表
 *              包含日期、商品、门店维度关联
 */
export const model = {
    name: 'FactInventorySnapshotModel',
    caption: '库存快照事实表',
    tableName: 'fact_inventory_snapshot',
    idColumn: 'snapshot_key',

    // 维度定义
    dimensions: [
        {
            name: 'snapshotDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '快照日期',
            description: '库存快照记录的日期',
            keyDescription: '日期主键，格式yyyyMMdd，如20240101',

            properties: [
                { column: 'year', caption: '年', description: '快照年份' },
                { column: 'quarter', caption: '季度', description: '快照季度（1-4）' },
                { column: 'month', caption: '月', description: '快照月份（1-12）' },
                { column: 'month_name', caption: '月份名称', description: '快照月份中文名' },
                { column: 'day_of_week', caption: '周几', description: '快照在周几（1=周一）' }
            ]
        },
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: '商品',
            description: '库存商品信息',
            keyDescription: '商品代理键，自增整数',

            properties: [
                { column: 'product_id', caption: '商品ID', description: '商品唯一标识' },
                { column: 'category_name', caption: '品类名称', description: '商品一级分类名称' },
                { column: 'sub_category_name', caption: '子品类名称', description: '商品二级分类名称' },
                { column: 'brand', caption: '品牌', description: '商品品牌名称' }
            ]
        },
        {
            name: 'store',
            tableName: 'dim_store',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            captionColumn: 'store_name',
            caption: '门店',
            description: '库存所在门店',
            keyDescription: '门店代理键，自增整数',

            properties: [
                { column: 'store_id', caption: '门店ID', description: '门店唯一标识' },
                { column: 'store_type', caption: '门店类型', description: '门店类型：直营店/加盟店/旗舰店' },
                { column: 'province', caption: '省份', description: '门店所在省份' },
                { column: 'city', caption: '城市', description: '门店所在城市' }
            ]
        }
    ],

    // 属性定义
    properties: [
        {
            column: 'snapshot_key',
            caption: '快照代理键',
            type: 'LONG'
        },
        {
            column: 'created_at',
            caption: '创建时间',
            type: 'DATETIME'
        }
    ],

    // 度量定义
    measures: [
        {
            column: 'quantity_on_hand',
            caption: '在库数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'quantity_reserved',
            caption: '预留数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'quantity_available',
            caption: '可用数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'unit_cost',
            caption: '单位成本',
            type: 'MONEY',
            aggregation: 'avg'
        },
        {
            column: 'inventory_value',
            caption: '库存价值',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
