/**
 * Odoo Purchase Document Flow Model.
 *
 * @description Demo-level purchase cross-document facts connecting purchase
 *              orders, inbound receipts, and vendor bills. Real Odoo flows can
 *              be reconstructed from procurement, stock, and accounting
 *              relations; this table makes the relationship explicit for
 *              semantic-query fixtures.
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'OdooPurchaseDocumentFlowModel',
    caption: 'Purchase Document Flows',
    tableName: 'purchase_document_flow',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'purchaseOrder',
            tableName: 'purchase_order',
            foreignKey: 'purchase_order_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Purchase Order',
            description: 'Source purchase order in the procurement flow.',
            properties: [
                { column: 'state', caption: 'Purchase Status', type: 'STRING', dictRef: dicts.purchase_order_state },
                { column: 'invoice_status', caption: 'Purchase Billing Status', type: 'STRING', dictRef: dicts.purchase_invoice_status },
                { column: 'date_order', caption: 'Order Date', type: 'DATETIME',
                  timeRole: 'business_date', recommendedUse: 'Use for procurement order-period analysis.' },
                { column: 'date_approve', caption: 'Confirmation Date', type: 'DATETIME',
                  timeRole: 'approval_date', recommendedUse: 'Use for purchase confirmation-cycle analysis.' },
                { column: 'amount_total', caption: 'Purchase Total', type: 'MONEY' }
            ]
        },
        {
            name: 'receiptPicking',
            tableName: 'stock_picking',
            foreignKey: 'receipt_picking_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Receipt',
            description: 'Inbound stock receipt linked to the purchase order.',
            properties: [
                { column: 'state', caption: 'Receipt Status', type: 'STRING', dictRef: dicts.stock_picking_state },
                { column: 'origin', caption: 'Receipt Source Document', type: 'STRING' },
                { column: 'scheduled_date', caption: 'Scheduled Receipt Date', type: 'DATETIME',
                  timeRole: 'business_date', recommendedUse: 'Use for planned receipt-period analysis.' },
                { column: 'date_done', caption: 'Receipt Done Date', type: 'DATETIME',
                  timeRole: 'completion_date', recommendedUse: 'Use for actual receipt completion analysis.' }
            ]
        },
        {
            name: 'billMove',
            tableName: 'account_move',
            foreignKey: 'bill_move_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Vendor Bill',
            description: 'Vendor bill linked to the purchase order. Must be move_type=in_invoice for ordinary AP bill analysis.',
            properties: [
                { column: 'move_type', caption: 'Bill Type', type: 'STRING', dictRef: dicts.account_move_type },
                { column: 'state', caption: 'Bill Status', type: 'STRING', dictRef: dicts.account_move_state },
                { column: 'payment_state', caption: 'Bill Payment Status', type: 'STRING', dictRef: dicts.account_payment_state },
                { column: 'invoice_origin', caption: 'Bill Source Document', type: 'STRING' },
                { column: 'invoice_date', caption: 'Bill Date', type: 'DAY',
                  timeRole: 'business_date', recommendedUse: 'Use for bill-period analysis.' },
                { column: 'invoice_date_due', caption: 'Bill Due Date', type: 'DAY',
                  timeRole: 'due_date', recommendedUse: 'Use for payable aging analysis.' },
                { column: 'amount_total', caption: 'Bill Total', type: 'MONEY' },
                { column: 'amount_residual', caption: 'Bill Residual', type: 'MONEY' }
            ]
        },
        {
            name: 'vendor',
            tableName: 'res_partner',
            foreignKey: 'vendor_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Vendor',
            description: 'Supplier on the purchase document flow.'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'flow_status', caption: 'Flow Status', type: 'STRING',
          description: 'Demo cross-document status, e.g. received_billed_paid or received_billed_open.' },
        { column: 'receipt_status', caption: 'Receipt Status Snapshot', type: 'STRING', dictRef: dicts.stock_picking_state },
        { column: 'billing_status', caption: 'Billing Status Snapshot', type: 'STRING', dictRef: dicts.purchase_invoice_status },
        { column: 'payment_state', caption: 'Payment Status Snapshot', type: 'STRING', dictRef: dicts.account_payment_state },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'ordered_amount', caption: 'Ordered Amount', type: 'MONEY', aggregation: 'sum' },
        { column: 'billed_amount', caption: 'Billed Amount', type: 'MONEY', aggregation: 'sum' },
        { column: 'bill_residual', caption: 'Bill Residual', type: 'MONEY', aggregation: 'sum' },
        {
            column: 'id',
            name: 'flowCount',
            caption: 'Flow Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
