/**
 * Odoo Account Move Line Model (account.move.line)
 *
 * @description Journal items / invoice lines with account, journal, partner, product, and company dimensions.
 *              The most granular accounting table — each row is a debit/credit line.
 *              Filter by display_type='product' for invoice product lines.
 */
import { dicts } from '../dicts.fsscript';
import { jsonbCaption } from '../odoo17.fsscript';

export const model = {
    name: 'OdooAccountMoveLineModel',
    caption: 'Journal Items',
    tableName: 'account_move_line',
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
            description: 'Parent journal entry / invoice',
            properties: [
                { column: 'move_type', caption: 'Entry Type', type: 'STRING' },
                { column: 'state', caption: 'Entry Status', type: 'STRING' },
                { column: 'invoice_user_id', caption: 'Salesperson ID', type: 'INTEGER' },
                { column: 'payment_state', caption: 'Payment Status', type: 'STRING' }
            ]
        },
        {
            name: 'account',
            tableName: 'account_account',
            foreignKey: 'account_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Account',
            description: 'GL account',
            properties: [
                { column: 'code', caption: 'Account Code', type: 'STRING' },
                { column: 'account_type', caption: 'Account Type', type: 'STRING' }
            ]
        },
        {
            name: 'journal',
            tableName: 'account_journal',
            foreignKey: 'journal_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Journal',
            description: 'Accounting journal',
            properties: [
                { column: 'code', caption: 'Journal Code', type: 'STRING' },
                { column: 'type', caption: 'Journal Type', type: 'STRING' }
            ]
        },
        {
            name: 'partner',
            tableName: 'res_partner',
            foreignKey: 'partner_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Partner',
            description: 'Related partner',
            properties: [
                { column: 'email', caption: 'Email', type: 'STRING' },
                { column: 'phone', caption: 'Phone', type: 'STRING' },
                { column: 'city', caption: 'City', type: 'STRING' },
                { column: 'is_company', caption: 'Is Company', type: 'BOOL' }
            ]
        },
        {
            name: 'partnerCountry',
            tableName: 'res_country',
            foreignKey: 'country_id',
            primaryKey: 'id',
            joinTo: 'partner',
            captionDef: jsonbCaption(),
            caption: 'Partner Country',
            description: 'Country of the journal item partner',
            properties: [
                { column: 'code', caption: 'Country Code', type: 'STRING' }
            ]
        },
        {
            name: 'product',
            tableName: 'product_product',
            foreignKey: 'product_id',
            primaryKey: 'id',
            captionDef: jsonbCaption('default_code'),
            caption: 'Product',
            description: 'Product (for invoice lines)'
        },
        {
            name: 'company',
            tableName: 'res_company',
            foreignKey: 'company_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Company',
            description: 'Operating company',
            closureTableName: 'res_company_closure',
            parentKey: 'parent_id',
            childKey: 'company_id'
        },
        {
            name: 'currency',
            tableName: 'res_currency',
            foreignKey: 'currency_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Currency',
            description: 'Line currency'
        },
        {
            name: 'invoiceDate',
            tableName: 'dim_date',
            foreignKey: 'invoice_date',
            primaryKey: 'full_date',
            captionColumn: 'year_month',
            caption: 'Invoice Date',
            description: 'Calendar dimension for the account.move.line invoice_date field.',
            type: 'DATE',
            properties: [
                { column: 'year', caption: 'Invoice Year', type: 'INTEGER' },
                { column: 'quarter', caption: 'Invoice Quarter', type: 'INTEGER' },
                { column: 'month', caption: 'Invoice Month', type: 'INTEGER' },
                { column: 'week_of_year', name: 'week', caption: 'Invoice Week', type: 'INTEGER' },
                { column: 'day_of_month', name: 'dayOfMonth', caption: 'Invoice Day of Month', type: 'INTEGER' },
                { column: 'year_month', name: 'yearMonth', caption: 'Invoice Year-Month', type: 'STRING' },
                { column: 'year_quarter', name: 'yearQuarter', caption: 'Invoice Year-Quarter', type: 'STRING' }
            ]
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'move_name', caption: 'Journal Entry Number', type: 'STRING', description: 'Parent entry reference (e.g. INV/2025/0001)' },
        { column: 'name', caption: 'Label', type: 'STRING', description: 'Line description' },
        { column: 'ref', caption: 'Reference', type: 'STRING' },
        { column: 'parent_state', caption: 'Parent Status', type: 'STRING', dictRef: dicts.account_move_state,
          description: 'Parent journal entry status (denormalized from account.move.state). '
              + 'enum: draft / posted / cancel. AR / AP queries should filter parent_state=posted '
              + 'to exclude unposted drafts — mirrors move.state when queried through the move '
              + 'dimension ($move$state).' },
        { column: 'display_type', caption: 'Display Type', type: 'STRING', dictRef: dicts.move_line_display_type },
        { column: 'date', caption: 'Date', type: 'DAY',
          timeRole: 'posting_date', recommendedUse: 'Use for GL posting-period analysis.' },
        { column: 'invoice_date', caption: 'Invoice Date', type: 'DAY',
          timeRole: 'business_date', recommendedUse: 'Primary invoice line business date for invoice trend and period pivot queries.' },
        { column: 'date_maturity', caption: 'Due Date', type: 'DAY',
          description: 'Maturity / due date for this AR or AP line (matches account.move.line.'
              + 'date_maturity). Compared against now() to detect overdue rows — a line is '
              + 'considered overdue when date_maturity < now() AND amount_residual > 0 AND '
              + 'parent_state = posted.',
          timeRole: 'due_date', recommendedUse: 'Use for AR/AP aging and overdue receivable/payable analysis.' },
        { column: 'matching_number', caption: 'Matching Number', type: 'STRING' },
        { column: 'reconciled', caption: 'Reconciled', type: 'BOOL',
          description: 'TRUE once this line is fully reconciled against one or more payment '
              + 'lines. Reconciled lines have amount_residual = 0 and parent move.payment_state '
              + 'in (paid, reversed); they are excluded from AR / AP aging reports.' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'debit', caption: 'Debit', type: 'MONEY', aggregation: 'sum' },
        { column: 'credit', caption: 'Credit', type: 'MONEY', aggregation: 'sum' },
        { column: 'balance', caption: 'Balance', type: 'MONEY', aggregation: 'sum', description: 'debit - credit' },
        { column: 'amount_currency', caption: 'Amount in Currency', type: 'MONEY', aggregation: 'sum' },
        { column: 'amount_residual', caption: 'Residual Amount', type: 'MONEY', aggregation: 'sum',
          description: 'Outstanding balance on this line in company currency. Positive for both '
              + 'AR and AP while unpaid; drops to 0 once reconciled. The canonical AR "open '
              + 'balance" measure. Combine with parent_state=posted and move.payment_state in '
              + '(not_paid, partial, in_payment) to compute AR outstanding / overdue totals.' },
        { column: 'quantity', caption: 'Quantity', type: 'NUMBER', aggregation: 'sum' },
        { column: 'price_unit', caption: 'Unit Price', type: 'MONEY' },
        { column: 'price_subtotal', caption: 'Subtotal', type: 'MONEY', aggregation: 'sum' },
        { column: 'price_total', caption: 'Total', type: 'MONEY', aggregation: 'sum' },
        { column: 'discount', caption: 'Discount %', type: 'NUMBER' },
        {
            column: 'id',
            name: 'lineCount',
            caption: 'Line Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
