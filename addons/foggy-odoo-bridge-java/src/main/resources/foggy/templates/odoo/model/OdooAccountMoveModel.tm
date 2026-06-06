/**
 * Odoo Account Move Model (account.move)
 *
 * @description Journal entries / invoices / bills with partner, journal, and company dimensions.
 *              Use move_type to distinguish invoices from bills.
 */
import { dicts } from '../dicts.fsscript';
import { jsonbCaption } from '../odoo17.fsscript';

export const model = {
    name: 'OdooAccountMoveModel',
    caption: 'Invoices & Journal Entries',
    tableName: 'account_move',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'invoiceDate',
            foreignKey: 'invoice_date',
            primaryKey: 'invoice_date',
            captionColumn: 'invoice_date',
            caption: 'Invoice Date',
            description: 'Self date dimension backed by account_move.invoice_date without joining dim_date',
            type: 'DAY',
            timeRole: 'business_date',
            recommendedUse: 'Primary invoice/bill business date for timeWindow, revenue, AP, and period pivot queries.',
            properties: [
                {
                    column: 'invoice_date',
                    name: 'year',
                    caption: 'Invoice Year',
                    type: 'INTEGER',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `CAST(strftime('%Y', ${alias}.invoice_date) AS INTEGER)`; } },
                        postgresql: { builder: (alias) => { return `EXTRACT(YEAR FROM ${alias}.invoice_date)`; } },
                        mysql: { builder: (alias) => { return `YEAR(${alias}.invoice_date)`; } },
                        sqlserver: { builder: (alias) => { return `DATEPART(year, ${alias}.invoice_date)`; } }
                    }
                },
                {
                    column: 'invoice_date',
                    name: 'month',
                    caption: 'Invoice Month',
                    type: 'INTEGER',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `CAST(strftime('%m', ${alias}.invoice_date) AS INTEGER)`; } },
                        postgresql: { builder: (alias) => { return `EXTRACT(MONTH FROM ${alias}.invoice_date)`; } },
                        mysql: { builder: (alias) => { return `MONTH(${alias}.invoice_date)`; } },
                        sqlserver: { builder: (alias) => { return `DATEPART(month, ${alias}.invoice_date)`; } }
                    }
                },
                {
                    column: 'invoice_date',
                    name: 'yearMonth',
                    caption: 'Invoice Year-Month',
                    type: 'STRING',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `strftime('%Y-%m', ${alias}.invoice_date)`; } },
                        postgresql: { builder: (alias) => { return `TO_CHAR(${alias}.invoice_date, 'YYYY-MM')`; } },
                        mysql: { builder: (alias) => { return `DATE_FORMAT(${alias}.invoice_date, '%Y-%m')`; } },
                        sqlserver: { builder: (alias) => { return `CONVERT(char(7), ${alias}.invoice_date, 120)`; } }
                    }
                }
            ]
        },
        {
            name: 'partner',
            tableName: 'res_partner',
            foreignKey: 'partner_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Partner',
            description: 'Customer or vendor',
            properties: [
                { column: 'email', caption: 'Email', type: 'STRING' },
                { column: 'phone', caption: 'Phone', type: 'STRING' },
                { column: 'city', caption: 'City', type: 'STRING' },
                { column: 'country_id', caption: 'Country ID', type: 'INTEGER' },
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
            description: 'Country of the customer or vendor',
            properties: [
                { column: 'code', caption: 'Country Code', type: 'STRING' }
            ]
        },
        {
            name: 'journal',
            tableName: 'account_journal',
            foreignKey: 'journal_id',
            primaryKey: 'id',
            // Odoo 17: account_journal.name is JSONB
            captionDef: jsonbCaption(),
            caption: 'Journal',
            description: 'Accounting journal',
            properties: [
                { column: 'code', caption: 'Journal Code', type: 'STRING' },
                { column: 'type', caption: 'Journal Type', type: 'STRING' }
            ]
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
            description: 'Entry currency'
        },
        {
            name: 'salesperson',
            tableName: 'res_users',
            foreignKey: 'invoice_user_id',
            primaryKey: 'id',
            captionColumn: 'login',
            caption: 'Salesperson',
            description: 'Salesperson for customer invoices'
        },
        {
            name: 'salesTeam',
            tableName: 'crm_team',
            foreignKey: 'team_id',
            primaryKey: 'id',
            // Odoo 17: crm_team.name is JSONB
            captionDef: jsonbCaption(),
            caption: 'Sales Team',
            description: 'Sales team responsible'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'name', caption: 'Number', type: 'STRING', description: 'Invoice/entry number' },
        { column: 'move_type', caption: 'Type', type: 'STRING', dictRef: dicts.account_move_type,
          description: 'Journal entry type. enum: out_invoice (customer invoice) / in_invoice '
              + '(vendor bill) / out_refund (customer credit note) / in_refund (vendor refund) / '
              + 'entry (misc journal entry) / out_receipt (sales receipt) / in_receipt (purchase receipt). '
              + 'AR / AP reports filter by out_invoice and in_invoice respectively.' },
        { column: 'state', caption: 'Status', type: 'STRING', dictRef: dicts.account_move_state,
          description: 'Entry posting status. enum: draft (not posted) / posted (posted to GL) / '
              + 'cancel (cancelled). Only posted entries contribute to AR / AP balances.' },
        { column: 'date', caption: 'Accounting Date', type: 'DAY',
          timeRole: 'posting_date', recommendedUse: 'Use for GL posting-period analysis.' },
        { column: 'invoice_date_due', caption: 'Due Date', type: 'DAY',
          description: 'Invoice due date (mapped to account.move.line.date_maturity on the '
              + 'receivable/payable line — use dateMaturity via the move dimension when querying '
              + 'AR/AP aging).',
          timeRole: 'due_date', recommendedUse: 'Use for AR/AP aging and overdue analysis.' },
        { column: 'payment_state', caption: 'Payment Status', type: 'STRING', dictRef: dicts.account_payment_state,
          description: 'Payment reconciliation status. enum: not_paid / in_payment (bank sync pending) / '
              + 'paid (fully reconciled) / partial (partially reconciled) / reversed / invoicing_legacy '
              + '(pre-migration). AR/AP outstanding uses not_paid + partial + in_payment; '
              + 'paid / reversed / invoicing_legacy are excluded from aging.',
          dictionaryDiscovery: {
              enabled: true,
              strategy: 'group_by',
              maxValues: 20,
              refreshTtlSeconds: 3600,
              exposeToLlm: true,
              sensitive: false,
              aliases: {
                  open_receivable: {
                      values: ['not_paid', 'partial', 'in_payment'],
                      description: 'Receivable entries that are not fully settled'
                  },
                  closed_receivable: {
                      values: ['paid', 'reversed', 'invoicing_legacy'],
                      description: 'Receivable entries excluded from open aging'
                  },
                  open_payable: {
                      values: ['not_paid', 'partial', 'in_payment'],
                      description: 'Payable entries that are not fully settled'
                  },
                  closed_payable: {
                      values: ['paid', 'reversed', 'invoicing_legacy'],
                      description: 'Payable entries excluded from open aging'
                  }
              }
          }
        },
        { column: 'ref', caption: 'Reference', type: 'STRING' },
        { column: 'invoice_origin', caption: 'Source Document', type: 'STRING' },
        { column: 'narration', caption: 'Internal Note', type: 'STRING' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'amount_untaxed', caption: 'Untaxed Amount', type: 'MONEY', aggregation: 'sum' },
        { column: 'amount_tax', caption: 'Tax Amount', type: 'MONEY', aggregation: 'sum' },
        { column: 'amount_total', caption: 'Total', type: 'MONEY', aggregation: 'sum' },
        { column: 'amount_residual', caption: 'Amount Due', type: 'MONEY', aggregation: 'sum',
          description: 'Outstanding balance on this entry in company currency. Positive for AR '
              + '(customer invoices with money still owed) and AP (vendor bills with money still '
              + 'owed); drops to 0 once fully reconciled. Kept in sync with payment_state: rows '
              + 'with state=posted and payment_state in (not_paid, partial, in_payment) carry a '
              + 'non-zero residual; paid and reversed rows have residual = 0.' },
        { column: 'amount_untaxed_signed', caption: 'Untaxed Amount (Signed)', type: 'MONEY', aggregation: 'sum' },
        { column: 'amount_total_signed', caption: 'Total (Signed)', type: 'MONEY', aggregation: 'sum' },
        { column: 'amount_residual_signed', caption: 'Amount Due (Signed)', type: 'MONEY', aggregation: 'sum' },
        {
            column: 'id',
            name: 'entryCount',
            caption: 'Entry Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
