/**
 * 库存快照事实表模型定义
 *
 * @description 电商测试数据 - 库存快照事实表
 *              包含日期、商品、门店维度关联
 */
import { buildDateDim, buildProductDim, buildStoreDim } from '../dimensions/common-dims.fsscript';
export const model = {
    name: 'FactInventorySnapshotModel',
    caption: '库存快照事实表',
    tableName: 'fact_inventory_snapshot',
    idColumn: 'snapshot_key',

    // 维度定义
    dimensions: [
        buildDateDim({ 
            name: 'snapshotDate', 
            caption: '快照日期', 
            description: '库存快照记录的日期', 
            contextPrefix: '快照',
            includeProperties: ['year', 'quarter', 'month', 'month_name', 'day_of_week']
        }),
        buildProductDim({ caption: '商品', description: '库存商品信息', contextPrefix: '', includeProperties: ['product_id', 'category_name', 'sub_category_name', 'brand'] }),
        buildStoreDim({ caption: '门店', description: '库存所在门店', contextPrefix: '' })
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
