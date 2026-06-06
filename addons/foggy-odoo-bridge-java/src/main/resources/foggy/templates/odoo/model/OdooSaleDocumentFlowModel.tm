/**
 * Odoo Sale Document Flow Model.
 *
 * @description Demo-level sales cross-document facts connecting sales orders,
 *              delivery orders, customer invoices, and customer receipts.
 *              Real Odoo flows can be reconstructed from sales, stock, and
 *              accounting relations; this table makes the relationship
 *              explicit for semantic-query fixtures.
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'OdooSaleDocumentFlowModel',
    caption: 'Sale Document Flows',
    tableName: 'sale_document_flow',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'saleOrder',
            tableName: 'sale_order',
            foreignKey: 'sale_order_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Sale Order',
            description: 'Source sales order in the order-to-cash flow.',
            properties: [
                { column: 'state', caption: 'Sale Status', type: 'STRING', dictRef: dicts.sale_order_state },
                { column: 'invoice_status', caption: 'Sale Invoice Status', type: 'STRING', dictRef: dicts.sale_invoice_status },
                { column: 'date_order', caption: 'Order Date', type: 'DATETIME',
                  timeRole: 'business_date', recommendedUse: 'Use for sales order-period analysis.' },
                { column: 'amount_total', caption: 'Sale Total', type: 'MONEY' }
            ]
        },
        {
            name: 'deliveryPicking',
            tableName: 'stock_picking',
            foreignKey: 'delivery_picking_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Delivery',
            description: 'Outbound stock delivery linked to the sales order.',
            properties: [
                { column: 'state', caption: 'Delivery Status', type: 'STRING', dictRef: dicts.stock_picking_state },
                { column: 'origin', caption: 'Delivery Source Document', type: 'STRING' },
                { column: 'scheduled_date', caption: 'Scheduled Delivery Date', type: 'DATETIME',
                  timeRole: 'business_date', recommendedUse: 'Use for planned delivery-period analysis.' },
                { column: 'date_done', caption: 'Delivery Done Date', type: 'DATETIME',
                  timeRole: 'completion_date', recommendedUse: 'Use for actual delivery completion analysis.' }
            ]
        },
        {
            name: 'invoiceMove',
            tableName: 'account_move',
            foreignKey: 'invoice_move_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Customer Invoice',
            description: 'Customer invoice linked to the sales order. Must be move_type=out_invoice for ordinary AR invoice analysis.',
            properties: [
                { column: 'move_type', caption: 'Invoice Type', type: 'STRING', dictRef: dicts.account_move_type },
                { column: 'state', caption: 'Invoice Status', type: 'STRING', dictRef: dicts.account_move_state },
                { column: 'payment_state', caption: 'Invoice Payment Status', type: 'STRING', dictRef: dicts.account_payment_state },
                { column: 'invoice_origin', caption: 'Invoice Source Document', type: 'STRING' },
                { column: 'invoice_date', caption: 'Invoice Date', type: 'DAY',
                  timeRole: 'business_date', recommendedUse: 'Use for customer invoice-period analysis.' },
                { column: 'invoice_date_due', caption: 'Invoice Due Date', type: 'DAY',
                  timeRole: 'due_date', recommendedUse: 'Use for receivable aging analysis.' },
                { column: 'amount_total', caption: 'Invoice Total', type: 'MONEY' },
                { column: 'amount_residual', caption: 'Invoice Residual', type: 'MONEY' }
            ]
        },
        {
            name: 'payment',
            tableName: 'account_payment',
            foreignKey: 'payment_id',
            primaryKey: 'id',
            captionColumn: 'payment_reference',
            caption: 'Customer Payment',
            description: 'Customer payment linked to the invoice. Customer collection analysis should require inbound customer payments.',
            properties: [
                { column: 'payment_type', caption: 'Payment Type', type: 'STRING', dictRef: dicts.payment_type },
                { column: 'partner_type', caption: 'Payment Partner Type', type: 'STRING', dictRef: dicts.payment_partner_type },
                { column: 'is_reconciled', caption: 'Payment Is Reconciled', type: 'BOOL' },
                { column: 'is_matched', caption: 'Payment Is Matched', type: 'BOOL' },
                { column: 'amount', caption: 'Payment Amount', type: 'MONEY' }
            ]
        },
        {
            name: 'customer',
            tableName: 'res_partner',
            foreignKey: 'customer_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Customer',
            description: 'Customer on the sales document flow.'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'flow_status', caption: 'Flow Status', type: 'STRING',
          description: 'Demo cross-document status, e.g. delivered_invoiced_paid or delivered_invoiced_partial.' },
        { column: 'delivery_status', caption: 'Delivery Status Snapshot', type: 'STRING', dictRef: dicts.stock_picking_state },
        { column: 'invoice_status', caption: 'Invoice Status Snapshot', type: 'STRING', dictRef: dicts.sale_invoice_status },
        { column: 'payment_state', caption: 'Payment Status Snapshot', type: 'STRING', dictRef: dicts.account_payment_state },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'ordered_amount', caption: 'Ordered Amount', type: 'MONEY', aggregation: 'sum' },
        { column: 'invoiced_amount', caption: 'Invoiced Amount', type: 'MONEY', aggregation: 'sum' },
        { column: 'invoice_residual', caption: 'Invoice Residual', type: 'MONEY', aggregation: 'sum' },
        { column: 'paid_amount', caption: 'Paid Amount', type: 'MONEY', aggregation: 'sum' },
        {
            column: 'id',
            name: 'flowCount',
            caption: 'Flow Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
