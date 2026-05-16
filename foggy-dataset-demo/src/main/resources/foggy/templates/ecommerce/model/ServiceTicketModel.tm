/**
 * 客服工单事实模型定义
 *
 * @description SLA DSL_CTE parity fixture - 客服工单事实表
 */
export const model = {
    name: 'ServiceTicketModel',
    caption: '客服工单事实',
    tableName: 'service_ticket',
    idColumn: 'ticket_id',

    dimensions: [
        {
            name: 'team',
            tableName: 'dim_team',
            foreignKey: 'team_id',
            primaryKey: 'team_id',
            captionColumn: 'team_name',
            caption: '团队',
            description: '客服工单所属团队',
            keyDescription: '团队ID，字符串格式',

            properties: [
                { column: 'team_id', caption: '团队ID', type: 'STRING', description: '团队唯一标识' },
                { column: 'team_name', caption: '团队名称', type: 'STRING', description: '团队显示名称' },
                { column: 'parent_id', caption: '上级团队', type: 'STRING', description: '上级团队ID' },
                { column: 'team_level', caption: '层级', type: 'INTEGER', description: '团队在组织树中的层级' },
                { column: 'manager_name', caption: '负责人', type: 'STRING', description: '团队负责人姓名' },
                { column: 'status', caption: '团队状态', type: 'STRING', description: '团队状态：启用/禁用' }
            ]
        }
    ],

    properties: [
        {
            column: 'ticket_id',
            caption: '工单ID',
            type: 'STRING'
        },
        {
            column: 'team_id',
            caption: '团队ID',
            type: 'STRING'
        },
        {
            column: 'created_at',
            caption: '创建时间',
            description: '工单创建时间，用于SLA时间范围过滤和首响耗时计算',
            type: 'DATETIME'
        },
        {
            column: 'first_response_at',
            caption: '首次响应时间',
            description: '客服首次响应时间；为空表示尚未响应，不计为SLA达成',
            type: 'DATETIME'
        },
        {
            column: 'resolved_at',
            caption: '解决时间',
            type: 'DATETIME'
        },
        {
            column: 'priority',
            caption: '优先级',
            description: '工单优先级枚举：P1=最高优先级，P2=普通高优先级，P3=标准优先级；用于显式SLA阈值映射，不表示工单生命周期状态。',
            type: 'STRING'
        },
        {
            column: 'status',
            caption: '工单状态',
            description: '工单状态：OPEN/RESOLVED',
            type: 'STRING'
        },
        {
            column: 'channel',
            caption: '来源渠道',
            type: 'STRING'
        }
    ],

    measures: [
        {
            name: 'ticketCount',
            caption: '工单数',
            aggregation: 'count',
            type: 'INTEGER'
        }
    ]
};
