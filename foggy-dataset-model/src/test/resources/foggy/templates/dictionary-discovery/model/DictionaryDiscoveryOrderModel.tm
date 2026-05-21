import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'DictionaryDiscoveryOrderModel',
    caption: '字典发现订单测试模型',
    tableName: 'fact_order',
    idColumn: 'order_key',

    properties: [
        {
            column: 'order_id',
            name: 'orderId',
            caption: '订单ID',
            type: 'STRING'
        },
        {
            column: 'order_status',
            name: 'status',
            caption: '订单状态',
            description: '订单生命周期状态。打开订单通常指待处理、已确认或处理中，不包括已发货、已完成、已取消或已退款。',
            type: 'STRING',
            dictRef: dicts.order_status,
            dictionaryDiscovery: {
                enabled: true,
                strategy: 'group_by',
                maxValues: 2,
                refreshTtlSeconds: 3600,
                exposeToLlm: true,
                sensitive: false,
                aliases: {
                    open_order: {
                        values: ['PENDING', 'CONFIRMED', 'PROCESSING'],
                        description: '打开订单，不包括已发货、已完成、已取消或已退款'
                    }
                }
            }
        }
    ],

    measures: [
        {
            column: 'total_amount',
            name: 'amount',
            caption: '订单总额',
            type: 'MONEY'
        }
    ]
};
