/**
 * Odoo Payment To Vendor Bill Match Model.
 *
 * @description Demo-level explicit payment-to-bill matching facts.  Real Odoo
 *              reconciliation is normally represented by account partial
 *              reconcile / move-line relations; this table makes that
 *              relationship visible for semantic-query fixtures.
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'OdooAccountPaymentBillMatchModel',
    caption: 'Payment To Vendor Bill Matches',
    tableName: 'account_payment_bill_match',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'payment',
            tableName: 'account_payment',
            foreignKey: 'payment_id',
            primaryKey: 'id',
            captionColumn: 'payment_reference',
            caption: 'Payment',
            description: 'Matched payment record. Vendor bill matching should require outbound supplier payments.',
            properties: [
                { column: 'payment_type', caption: 'Payment Type', type: 'STRING', dictRef: dicts.payment_type },
                { column: 'partner_type', caption: 'Partner Type', type: 'STRING', dictRef: dicts.payment_partner_type },
                { column: 'is_reconciled', caption: 'Payment Is Reconciled', type: 'BOOL' },
                { column: 'is_matched', caption: 'Payment Is Matched', type: 'BOOL' },
                { column: 'amount', caption: 'Payment Amount', type: 'MONEY' },
                { column: 'amount_company_currency_signed', caption: 'Payment Amount Signed', type: 'MONEY' }
            ]
        },
        {
            name: 'billMove',
            tableName: 'account_move',
            foreignKey: 'bill_move_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Vendor Bill',
            description: 'Matched vendor bill. Must be move_type=in_invoice for AP payment-to-bill analysis.',
            properties: [
                { column: 'move_type', caption: 'Bill Type', type: 'STRING', dictRef: dicts.account_move_type },
                { column: 'state', caption: 'Bill Status', type: 'STRING', dictRef: dicts.account_move_state },
                { column: 'payment_state', caption: 'Bill Payment Status', type: 'STRING', dictRef: dicts.account_payment_state },
                { column: 'invoice_date', caption: 'Bill Date', type: 'DAY',
                  timeRole: 'business_date', recommendedUse: 'Use for bill-period analysis.' },
                { column: 'invoice_date_due', caption: 'Bill Due Date', type: 'DAY',
                  timeRole: 'due_date', recommendedUse: 'Use for payable aging before/after matching.' },
                { column: 'amount_total', caption: 'Bill Total', type: 'MONEY' },
                { column: 'amount_residual', caption: 'Bill Residual', type: 'MONEY' }
            ]
        },
        {
            name: 'payableLine',
            tableName: 'account_move_line',
            foreignKey: 'payable_line_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Payable Line',
            description: 'Matched payable move line on the vendor bill.',
            properties: [
                { column: 'date_maturity', caption: 'Due Date', type: 'DAY',
                  timeRole: 'due_date', recommendedUse: 'Use for payable aging on matched lines.' },
                { column: 'reconciled', caption: 'Line Reconciled', type: 'BOOL' },
                { column: 'matching_number', caption: 'Matching Number', type: 'STRING' },
                { column: 'amount_residual', caption: 'Line Residual', type: 'MONEY' }
            ]
        },
        {
            name: 'vendor',
            tableName: 'res_partner',
            foreignKey: 'partner_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Vendor',
            description: 'Vendor on the matched bill'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'matched_date', caption: 'Matched Date', type: 'DAY',
          timeRole: 'business_date', recommendedUse: 'Use for payment-to-bill matching trend analysis.' },
        { column: 'match_status', caption: 'Match Status', type: 'STRING',
          description: 'Demo matching status. matched means the payment is linked to the vendor bill.' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'matched_amount', caption: 'Matched Amount', type: 'MONEY', aggregation: 'sum',
          description: 'Amount of the payment allocated to the matched vendor bill.' },
        {
            column: 'id',
            name: 'matchCount',
            caption: 'Match Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
