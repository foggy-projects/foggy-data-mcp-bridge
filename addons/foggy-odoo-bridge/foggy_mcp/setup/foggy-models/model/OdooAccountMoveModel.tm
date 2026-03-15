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
    idColumn: 'id',

    dimensions: [
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
        { column: 'move_type', caption: 'Type', type: 'STRING', dictRef: dicts.account_move_type },
        { column: 'state', caption: 'Status', type: 'STRING', dictRef: dicts.account_move_state },
        { column: 'date', caption: 'Accounting Date', type: 'DAY' },
        { column: 'invoice_date', caption: 'Invoice Date', type: 'DAY' },
        { column: 'invoice_date_due', caption: 'Due Date', type: 'DAY' },
        { column: 'payment_state', caption: 'Payment Status', type: 'STRING', dictRef: dicts.account_payment_state },
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
        { column: 'amount_residual', caption: 'Amount Due', type: 'MONEY', aggregation: 'sum' },
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
