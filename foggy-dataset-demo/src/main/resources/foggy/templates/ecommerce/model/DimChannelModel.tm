/**
 * 渠道维度模型定义
 *
 * @description 电商测试数据 - 销售渠道维度表
 */
export const model = {
    name: 'DimChannelModel',
    caption: '渠道维度',
    tableName: 'dim_channel',
    idColumn: 'channel_key',

    dimensions: [],

    properties: [
        {
            column: 'channel_key',
            caption: '渠道代理键',
            type: 'INTEGER'
        },
        {
            column: 'channel_id',
            caption: '渠道业务ID',
            type: 'STRING'
        },
        {
            column: 'channel_name',
            caption: '渠道名称',
            type: 'STRING'
        },
        {
            column: 'channel_type',
            caption: '渠道类型',
            type: 'STRING'
        },
        {
            column: 'platform',
            caption: '平台',
            type: 'STRING'
        },
        {
            column: 'status',
            caption: '状态',
            type: 'STRING'
        },
        {
            column: 'created_at',
            caption: '创建时间',
            type: 'DATETIME'
        }
    ],

    measures: []
};
