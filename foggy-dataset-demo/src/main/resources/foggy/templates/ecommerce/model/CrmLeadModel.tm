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

    properties: [
        {
            column: 'lead_id',
            caption: '线索ID',
            type: 'STRING'
        },
        {
            column: 'created_at',
            caption: '创建时间',
            description: '线索创建时间，用于漏斗统计时间范围过滤',
            type: 'DATETIME'
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
            description: '非空表示该线索已转化为订单；P0.16 CRM漏斗签名字段',
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
