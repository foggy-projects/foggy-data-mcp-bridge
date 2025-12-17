/**
 * MCP 工具调用审计日志模型
 *
 * @description MongoDB 文档模型示例 - 用于记录和查询 MCP 工具调用日志
 */
import '@mcpMongoTemplate';
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'McpAuditLogModel',
    caption: 'MCP工具调用日志',
    tableName: 'mcp_tool_audit_log',
    idColumn: '_id',
    type: 'mongo',
    mongoTemplate: mcpMongoTemplate,

    // MongoDB 模型没有维度（不做 join）
    // 所有字段都定义在 properties 中

    properties: [
        {
            column: '_id',
            name: 'id',
            caption: '日志ID',
            type: 'STRING'
        },
        {
            column: 'traceId',
            name: 'traceId',
            caption: 'AI会话ID',
            type: 'STRING',
            description: '一次完整AI执行的唯一标识，贯穿多次工具调用'
        },
        {
            column: 'requestId',
            name: 'requestId',
            caption: '请求ID',
            type: 'STRING',
            description: '单次HTTP请求的唯一标识'
        },
        {
            column: 'toolName',
            name: 'toolName',
            caption: '工具名称',
            type: 'STRING',
            dictRef: dicts.tool_name
        },
        {
            column: 'userRole',
            name: 'userRole',
            caption: '用户角色',
            type: 'STRING',
            dictRef: dicts.user_role
        },
        {
            column: 'authorization',
            name: 'authorization',
            caption: '授权信息',
            type: 'STRING',
            description: '用户授权token（已脱敏）'
        },
        {
            column: 'timestamp',
            name: 'timestamp',
            caption: '调用时间',
            type: 'DATETIME'
        },
        {
            column: 'durationMs',
            name: 'durationMs',
            caption: '执行耗时(ms)',
            type: 'LONG'
        },
        {
            column: 'success',
            name: 'success',
            caption: '是否成功',
            type: 'BOOL'
        },
        {
            column: 'errorType',
            name: 'errorType',
            caption: '错误类型',
            type: 'STRING',
            dictRef: dicts.error_type
        },
        {
            column: 'errorMessage',
            name: 'errorMessage',
            caption: '错误信息',
            type: 'STRING'
        },
        {
            column: 'resultSummary',
            name: 'resultSummary',
            caption: '结果摘要',
            type: 'STRING'
        },
        {
            column: 'clientIp',
            name: 'clientIp',
            caption: '客户端IP',
            type: 'STRING'
        },
        {
            column: 'requestPath',
            name: 'requestPath',
            caption: '请求路径',
            type: 'STRING'
        }
    ],

    measures: [
        {
            column: 'durationMs',
            name: 'avgDuration',
            caption: '平均耗时',
            type: 'LONG',
            aggregation: 'avg'
        },
        {
            column: 'durationMs',
            name: 'maxDuration',
            caption: '最大耗时',
            type: 'LONG',
            aggregation: 'max'
        },
        {
            column: 'durationMs',
            name: 'minDuration',
            caption: '最小耗时',
            type: 'LONG',
            aggregation: 'min'
        }
    ]
};
