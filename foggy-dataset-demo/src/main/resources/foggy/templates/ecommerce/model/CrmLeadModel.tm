/**
 * CRM 线索事实模型定义
 *
 * @description CRM DSL_CTE parity fixture - 线索转商机/订单漏斗事实表
 */
export const model = {
    name: 'CrmLeadModel',
    caption: 'CRM线索事实',
    tableName: 'crm_lead',
    idColumn: 'lead_id',

    dimensions: [
        {
            name: 'createdAt',
            foreignKey: 'created_at',
            primaryKey: 'created_at',
            captionColumn: 'created_at',
            caption: '创建时间',
            description: '线索创建时间 self 时间维度，用于漏斗统计时间范围过滤、月度趋势和转化率分析',
            type: 'DATETIME',
            timeRole: 'business_date',
            recommendedUse: 'CRM lead-created attribution time axis for funnel conversion trend queries.',
            properties: [
                {
                    column: 'created_at',
                    name: 'year',
                    caption: '创建年份',
                    description: '线索创建年份，用于按年分析CRM漏斗',
                    type: 'INTEGER',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `CAST(strftime('%Y', ${alias}.created_at) AS INTEGER)`; } },
                        postgresql: { builder: (alias) => { return `EXTRACT(YEAR FROM ${alias}.created_at)`; } },
                        mysql: { builder: (alias) => { return `YEAR(${alias}.created_at)`; } },
                        sqlserver: { builder: (alias) => { return `DATEPART(year, ${alias}.created_at)`; } }
                    }
                },
                {
                    column: 'created_at',
                    name: 'month',
                    caption: '创建月份',
                    description: '线索创建月份（1-12），用于按月分析CRM漏斗转化率',
                    type: 'INTEGER',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `CAST(strftime('%m', ${alias}.created_at) AS INTEGER)`; } },
                        postgresql: { builder: (alias) => { return `EXTRACT(MONTH FROM ${alias}.created_at)`; } },
                        mysql: { builder: (alias) => { return `MONTH(${alias}.created_at)`; } },
                        sqlserver: { builder: (alias) => { return `DATEPART(month, ${alias}.created_at)`; } }
                    }
                },
                {
                    column: 'created_at',
                    name: 'yearMonth',
                    caption: '创建年月',
                    description: '线索创建年月，格式YYYY-MM，用于按自然月分析CRM漏斗转化率',
                    type: 'STRING',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `strftime('%Y-%m', ${alias}.created_at)`; } },
                        postgresql: { builder: (alias) => { return `TO_CHAR(${alias}.created_at, 'YYYY-MM')`; } },
                        mysql: { builder: (alias) => { return `DATE_FORMAT(${alias}.created_at, '%Y-%m')`; } },
                        sqlserver: { builder: (alias) => { return `CONVERT(char(7), ${alias}.created_at, 120)`; } }
                    }
                }
            ]
        }
    ],

    properties: [
        {
            column: 'lead_id',
            caption: '线索ID',
            type: 'STRING'
        },
        {
            column: 'lead_source',
            caption: '线索来源',
            description: '线索来源渠道，用于按来源统计线索、商机和订单转化',
            type: 'STRING'
        },
        {
            column: 'converted_opportunity_id',
            caption: '转化商机ID',
            description: '非空表示该线索已转化为商机；P0.16 CRM漏斗签名字段',
            type: 'STRING'
        },
        {
            column: 'converted_order_id',
            caption: '转化订单ID',
            description: '非空表示该线索已转化为订单；业务值对齐 FactOrderQueryModel.orderId，用于后续受治理跨模型漏斗关系评估；P0.16 CRM漏斗签名字段',
            type: 'STRING'
        }
    ],

    measures: [
        {
            name: 'leadCount',
            caption: '线索数',
            aggregation: 'count',
            type: 'INTEGER'
        }
    ]
};
