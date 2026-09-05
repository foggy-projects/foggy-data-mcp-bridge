import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'IntegerDictionaryOrderModel',
    caption: '整数字典订单测试模型',
    tableName: 'fact_order',
    idColumn: 'order_key',
    properties: [
        { column: 'order_id', name: 'orderId', caption: '订单ID', type: 'STRING' },
        {
            column: 'customer_key',
            name: 'customerCode',
            caption: '客户编码',
            type: 'INTEGER',
            dictRef: dicts.integer_customer
        }
    ]
};
