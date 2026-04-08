/**
 * Odoo Account Payment Model (account.payment)
 *
 * @description Payment records with partner, currency, journal, and company dimensions.
 *              Use payment_type to distinguish inbound (receive) from outbound (send) payments.
 */
import { dicts } from '../dicts.fsscript';
import { jsonbCaption } from '../odoo17.fsscript';

export const model = {
    name: 'OdooAccountPaymentModel',
    caption: 'Payments',
    tableName: 'account_payment',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'move',
            tableName: 'account_move',
            foreignKey: 'move_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Journal Entry',
            description: 'Linked journal entry',
            properties: [
                { column: 'company_id', caption: 'Company ID', type: 'INTEGER' },
                { column: 'date', caption: 'Accounting Date', type: 'DAY' },
                { column: 'state', caption: 'Entry Status', type: 'STRING' }
            ]
        },
        {
            name: 'partner',
            tableName: 'res_partner',
            foreignKey: 'partner_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Partner',
            description: 'Customer or vendor receiving/sending payment',
            properties: [
                { column: 'email', caption: 'Email', type: 'STRING' },
                { column: 'phone', caption: 'Phone', type: 'STRING' },
                { column: 'city', caption: 'City', type: 'STRING' },
                { column: 'country_id', caption: 'Country ID', type: 'INTEGER' },
                { column: 'is_company', caption: 'Is Company', type: 'BOOL' }
            ]
        },
        {
            name: 'currency',
            tableName: 'res_currency',
            foreignKey: 'currency_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Currency',
            description: 'Payment currency'
        },
        {
            name: 'destinationJournal',
            tableName: 'account_journal',
            foreignKey: 'destination_journal_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Destination Journal',
            description: 'Destination journal (for internal transfers)',
            properties: [
                { column: 'code', caption: 'Journal Code', type: 'STRING' },
                { column: 'type', caption: 'Journal Type', type: 'STRING' }
            ]
        },
        {
            name: 'paymentMethod',
            tableName: 'account_payment_method',
            foreignKey: 'payment_method_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Payment Method',
            description: 'Payment method (manual, check, electronic, etc.)',
            properties: [
                { column: 'code', caption: 'Method Code', type: 'STRING' },
                { column: 'payment_type', caption: 'Method Payment Type', type: 'STRING' }
            ]
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'payment_type', caption: 'Payment Type', type: 'STRING', dictRef: dicts.payment_type,
          description: 'inbound = receive money, outbound = send money' },
        { column: 'partner_type', caption: 'Partner Type', type: 'STRING', dictRef: dicts.payment_partner_type,
          description: 'customer or supplier' },
        { column: 'payment_reference', caption: 'Payment Reference', type: 'STRING' },
        { column: 'is_reconciled', caption: 'Is Reconciled', type: 'BOOL' },
        { column: 'is_matched', caption: 'Is Matched', type: 'BOOL' },
        { column: 'is_internal_transfer', caption: 'Is Internal Transfer', type: 'BOOL' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'amount', caption: 'Amount', type: 'MONEY', aggregation: 'sum' },
        { column: 'amount_company_currency_signed', caption: 'Amount (Company Currency, Signed)', type: 'MONEY', aggregation: 'sum' },
        {
            column: 'id',
            name: 'paymentCount',
            caption: 'Payment Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
