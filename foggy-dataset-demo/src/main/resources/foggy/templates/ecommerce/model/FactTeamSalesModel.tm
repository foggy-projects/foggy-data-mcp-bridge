/**
 * 团队销售事实模型定义
 *
 * @description 父子维度测试 - 团队销售事实表
 * @author foggy-dataset
 * @since 1.0.0
 */
export const model = {
    name: 'FactTeamSalesModel',
    caption: '团队销售事实',
    tableName: 'fact_team_sales',
    idColumn: 'sales_id',

    dimensions: [
        {
            name: 'team',
            tableName: 'dim_team',
            foreignKey: 'team_id',
            primaryKey: 'team_id',
            captionColumn: 'team_name',
            caption: '团队',
            description: '销售所属团队',
            keyDescription: '团队ID，字符串格式',

            // 父子维度配置
            closureTableName: 'team_closure',
            parentKey: 'parent_id',
            childKey: 'team_id',

            properties: [
                { column: 'team_id', caption: '团队ID', type: 'STRING', description: '团队唯一标识' },
                { column: 'team_name', caption: '团队名称', type: 'STRING', description: '团队显示名称' },
                { column: 'parent_id', caption: '上级团队', type: 'STRING', description: '上级团队ID' },
                { column: 'team_level', caption: '层级', type: 'INTEGER', description: '团队在组织树中的层级' },
                { column: 'manager_name', caption: '负责人', type: 'STRING', description: '团队负责人姓名' },
                { column: 'status', caption: '状态', type: 'STRING', description: '团队状态：启用/禁用' }
            ]
        },
        {
            name: 'date',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '日期',
            description: '销售发生的日期',
            keyDescription: '日期主键，格式yyyyMMdd，如20240101',
            type: 'DATETIME',

            properties: [
                { column: 'date_key', caption: '日期键', type: 'INTEGER', description: '日期主键' },
                { column: 'full_date', caption: '日期', type: 'DATE', description: '完整日期' },
                { column: 'year', caption: '年', type: 'INTEGER', description: '年份' },
                { column: 'quarter', caption: '季度', type: 'INTEGER', description: '季度（1-4）' },
                { column: 'month', caption: '月', type: 'INTEGER', description: '月份（1-12）' },
                { column: 'month_name', caption: '月份名称', type: 'STRING', description: '月份中文名' }
            ]
        }
    ],

    properties: [
        {
            column: 'sales_id',
            caption: '销售ID',
            type: 'INTEGER'
        },
        {
            column: 'team_id',
            caption: '团队ID',
            type: 'STRING'
        },
        {
            column: 'date_key',
            caption: '日期键',
            type: 'INTEGER'
        },
        {
            column: 'sales_amount',
            caption: '销售金额',
            type: 'MONEY'
        },
        {
            column: 'sales_count',
            caption: '销售笔数',
            type: 'INTEGER'
        },
        {
            column: 'created_at',
            caption: '创建时间',
            type: 'DATETIME'
        }
    ],

    measures: [
        {
            name: 'totalSalesAmount',
            caption: '销售总额',
            column: 'sales_amount',
            aggregation: 'sum',
            type: 'MONEY'
        },
        {
            name: 'totalSalesCount',
            caption: '销售笔数',
            column: 'sales_count',
            aggregation: 'sum',
            type: 'INTEGER'
        },
        {
            name: 'avgSalesAmount',
            caption: '平均销售额',
            column: 'sales_amount',
            aggregation: 'avg',
            type: 'MONEY'
        },
        {
            name: 'recordCount',
            caption: '记录数',
            aggregation: 'count',
            type: 'INTEGER'
        }
    ]
};
