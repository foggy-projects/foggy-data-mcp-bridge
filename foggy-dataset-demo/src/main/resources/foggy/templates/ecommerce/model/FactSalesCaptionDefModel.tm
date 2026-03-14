/**
 * captionDef 功能测试模型
 *
 * @description 测试维度的 captionDef 对象，验证 formulaDef 和 dialectFormulaDef 在 SQL 生成中的效果。
 *              使用 fact_sales 表和 dim_customer 维表。
 */
export const model = {
    name: 'FactSalesCaptionDefModel',
    caption: 'captionDef 测试模型',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        // 维度1: 使用 captionDef.column（等价于 captionColumn）
        {
            name: 'customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_key',
            primaryKey: 'customer_key',
            caption: '客户',
            description: '客户维度（captionDef.column 简单模式）',
            captionDef: {
                column: 'customer_name'
            }
        },
        // 维度2: 使用 captionDef.formulaDef（通用公式）
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            caption: '商品',
            description: '商品维度（captionDef.formulaDef 通用公式模式）',
            captionDef: {
                column: 'product_name',
                formulaDef: {
                    builder: (alias) => {
                        return `COALESCE(${alias}.product_name, 'Unknown')`;
                    },
                    description: '优先显示 product_name，null 时显示 Unknown'
                }
            }
        },
        // 维度3: 使用 captionDef.dialectFormulaDef（方言专属公式）
        {
            name: 'store',
            tableName: 'dim_store',
            foreignKey: 'store_key',
            primaryKey: 'store_key',
            caption: '门店',
            description: '门店维度（captionDef.dialectFormulaDef 方言专属模式）',
            captionDef: {
                column: 'store_name',
                dialectFormulaDef: {
                    sqlite: {
                        builder: (alias) => {
                            return `${alias}.store_name || ' [SQLite]'`;
                        },
                        description: 'SQLite 方言：|| 拼接标记'
                    },
                    postgresql: {
                        builder: (alias) => {
                            return `${alias}.store_name || ' [PG]'`;
                        },
                        description: 'PostgreSQL 方言：|| 拼接标记'
                    },
                    mysql: {
                        builder: (alias) => {
                            return `CONCAT(${alias}.store_name, ' [MySQL]')`;
                        },
                        description: 'MySQL 方言：CONCAT 拼接标记'
                    }
                }
            }
        }
    ],

    // 属性定义 — 测试 dialectFormulaDef 在属性上的效果
    properties: [
        // 属性1: 使用 dialectFormulaDef（方言专属）
        {
            column: 'order_id',
            caption: '订单号',
            type: 'STRING',
            dialectFormulaDef: {
                sqlite: {
                    builder: (alias) => { return `${alias}.order_id || '-sqlite'`; },
                    description: 'SQLite: || 拼接后缀'
                },
                postgresql: {
                    builder: (alias) => { return `${alias}.order_id || '-pg'`; },
                    description: 'PostgreSQL: || 拼接后缀'
                },
                mysql: {
                    builder: (alias) => { return `CONCAT(${alias}.order_id, '-mysql')`; },
                    description: 'MySQL: CONCAT 拼接后缀'
                }
            }
        },
        // 属性2: 使用 formulaDef（通用公式，无 dialectFormulaDef 回退）
        {
            column: 'order_status',
            caption: '订单状态',
            type: 'STRING',
            formulaDef: {
                builder: (alias) => { return `UPPER(${alias}.order_status)`; },
                description: '统一转大写'
            }
        },
        // 属性3: 无公式（纯 column）
        {
            column: 'payment_method',
            caption: '支付方式',
            type: 'STRING'
        }
    ],

    measures: [
        { column: 'sales_amount', name: 'salesAmount', caption: '销售金额', type: 'MONEY', aggregation: 'sum' },
        { column: 'quantity', name: 'quantity', caption: '数量', type: 'INTEGER', aggregation: 'sum' },
        // 度量: 使用 dialectFormulaDef（方言专属）
        {
            column: 'tax_amount',
            name: 'taxDialect',
            caption: '税额(方言)',
            type: 'MONEY',
            aggregation: 'sum',
            dialectFormulaDef: {
                sqlite: {
                    builder: (alias) => { return `${alias}.tax_amount * 1.1`; },
                    description: 'SQLite: 税额*1.1'
                },
                postgresql: {
                    builder: (alias) => { return `${alias}.tax_amount * 1.1`; },
                    description: 'PostgreSQL: 税额*1.1'
                },
                mysql: {
                    builder: (alias) => { return `${alias}.tax_amount * 1.1`; },
                    description: 'MySQL: 税额*1.1'
                }
            }
        },
        // 度量: 使用 formulaDef（通用公式回退）
        {
            column: 'tax_amount',
            name: 'taxGeneric',
            caption: '税额(通用公式)',
            type: 'MONEY',
            aggregation: 'sum',
            formulaDef: {
                builder: (alias) => { return `${alias}.tax_amount + 100`; },
                description: '税额+100'
            }
        }
    ]
};
