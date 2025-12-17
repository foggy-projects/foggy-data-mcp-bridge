/**
 * 日期维度模型定义
 *
 * @description 电商测试数据 - 日期维度表
 */
export const model = {
    name: 'DimDateModel',
    caption: '日期维度',
    tableName: 'dim_date',
    idColumn: 'date_key',

    // 维度定义（日期表本身作为基础维度）
    dimensions: [],

    // 属性定义
    properties: [
        {
            column: 'date_key',
            caption: '日期键',
            description: '日期主键，格式为yyyyMMdd的整数，如20240101',
            type: 'INTEGER'
        },
        {
            column: 'full_date',
            caption: '完整日期',
            description: '完整日期，格式为yyyy-MM-dd',
            type: 'DATE'
        },
        {
            column: 'year',
            caption: '年',
            description: '年份，如2024',
            type: 'INTEGER'
        },
        {
            column: 'quarter',
            caption: '季度',
            description: '季度数字，1-4表示第一到第四季度',
            type: 'INTEGER'
        },
        {
            column: 'month',
            caption: '月',
            description: '月份数字，1-12',
            type: 'INTEGER'
        },
        {
            column: 'month_name',
            caption: '月份名称',
            description: '月份中文名，如一月、二月、十二月',
            type: 'STRING'
        },
        {
            column: 'week_of_year',
            caption: '年度周数',
            description: '一年中的第几周，1-53',
            type: 'INTEGER'
        },
        {
            column: 'day_of_month',
            caption: '月度天数',
            description: '一个月中的第几天，1-31',
            type: 'INTEGER'
        },
        {
            column: 'day_of_week',
            caption: '周几',
            description: '一周中的第几天，1=周一，7=周日',
            type: 'INTEGER'
        },
        {
            column: 'day_name',
            caption: '星期名称',
            description: '星期中文名，如周一、周二、周日',
            type: 'STRING'
        },
        {
            column: 'is_weekend',
            caption: '是否周末',
            description: '是否为周末（周六或周日），true/false',
            type: 'BOOL'
        },
        {
            column: 'is_holiday',
            caption: '是否节假日',
            description: '是否为法定节假日，true/false',
            type: 'BOOL'
        },
        {
            column: 'fiscal_year',
            caption: '财年',
            description: '财务年度，可能与自然年不同',
            type: 'INTEGER'
        },
        {
            column: 'fiscal_quarter',
            caption: '财季',
            description: '财务季度，1-4',
            type: 'INTEGER'
        }
    ],

    measures: []
};
