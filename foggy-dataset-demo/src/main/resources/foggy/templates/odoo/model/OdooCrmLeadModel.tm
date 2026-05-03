/**
 * Odoo CRM Lead/Opportunity Model (crm.lead)
 *
 * @description CRM pipeline: leads and opportunities with stage, salesperson, team and revenue tracking
 */
import { dicts } from '../dicts.fsscript';
import { jsonbCaption } from '../odoo17.fsscript';

export const model = {
    name: 'OdooCrmLeadModel',
    caption: 'CRM Leads & Opportunities',
    tableName: 'crm_lead',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'stage',
            tableName: 'crm_stage',
            foreignKey: 'stage_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Stage',
            description: 'Pipeline stage (New, Qualified, Proposition, Won)'
        },
        {
            name: 'partner',
            tableName: 'res_partner',
            foreignKey: 'partner_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Customer',
            description: 'Associated customer or company',
            properties: [
                { column: 'email', caption: 'Email', type: 'STRING' },
                { column: 'phone', caption: 'Phone', type: 'STRING' },
                { column: 'city', caption: 'City', type: 'STRING' },
                { column: 'country_id', caption: 'Country ID', type: 'INTEGER' },
                { column: 'is_company', caption: 'Is Company', type: 'BOOL' }
            ]
        },
        {
            name: 'salesperson',
            tableName: 'res_users',
            foreignKey: 'user_id',
            primaryKey: 'id',
            captionColumn: 'login',
            caption: 'Salesperson',
            description: 'Assigned salesperson'
        },
        {
            name: 'salesTeam',
            tableName: 'crm_team',
            foreignKey: 'team_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Sales Team',
            description: 'Sales team responsible'
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
            name: 'country',
            tableName: 'res_country',
            foreignKey: 'country_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Country',
            description: 'Lead country'
        },
        {
            name: 'lostReason',
            tableName: 'crm_lost_reason',
            foreignKey: 'lost_reason_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Lost Reason',
            description: 'Reason for losing the deal'
        },
        {
            name: 'campaign',
            tableName: 'utm_campaign',
            foreignKey: 'campaign_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Campaign',
            description: 'Marketing campaign source'
        },
        {
            name: 'utmSource',
            tableName: 'utm_source',
            foreignKey: 'source_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Source',
            description: 'UTM source (e.g. Google, Newsletter)'
        },
        {
            name: 'utmMedium',
            tableName: 'utm_medium',
            foreignKey: 'medium_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Medium',
            description: 'UTM medium (e.g. email, banner, social)'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'name', caption: 'Lead Name', type: 'STRING', description: 'Opportunity or lead title' },
        { column: 'type', caption: 'Type', type: 'STRING', dictRef: dicts.crm_lead_type, description: 'Lead or Opportunity' },
        { column: 'priority', caption: 'Priority', type: 'STRING', dictRef: dicts.crm_lead_priority },
        { column: 'active', caption: 'Active', type: 'BOOL' },
        { column: 'contact_name', caption: 'Contact Name', type: 'STRING' },
        { column: 'partner_name', caption: 'Company Name', type: 'STRING', description: 'Company name entered on lead' },
        { column: 'email_from', caption: 'Email', type: 'STRING' },
        { column: 'phone', caption: 'Phone', type: 'STRING' },
        { column: 'city', caption: 'City', type: 'STRING' },
        { column: 'date_deadline', caption: 'Expected Closing', type: 'DAY',
          timeRole: 'business_date', recommendedUse: 'Primary CRM forecast date for expected revenue, opportunity closing, and period pivot queries.' },
        { column: 'date_open', caption: 'Assigned Date', type: 'DATETIME', description: 'Date when lead was assigned to a salesperson',
          timeRole: 'assigned_date', recommendedUse: 'Use for lead assignment and sales response analysis.' },
        { column: 'date_closed', caption: 'Closed Date', type: 'DATETIME', description: 'Date when lead was won or lost',
          timeRole: 'closed_date', recommendedUse: 'Use for won/lost closure trend analysis.' },
        { column: 'date_conversion', caption: 'Conversion Date', type: 'DATETIME', description: 'Date when lead was converted to opportunity' },
        { column: 'date_last_stage_update', caption: 'Last Stage Update', type: 'DATETIME' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'expected_revenue', caption: 'Expected Revenue', type: 'MONEY', aggregation: 'sum' },
        { column: 'prorated_revenue', caption: 'Prorated Revenue', type: 'MONEY', aggregation: 'sum', description: 'Revenue weighted by probability' },
        { column: 'recurring_revenue', caption: 'Recurring Revenue', type: 'MONEY', aggregation: 'sum' },
        { column: 'recurring_revenue_monthly', caption: 'Monthly Recurring Revenue', type: 'MONEY', aggregation: 'sum' },
        { column: 'probability', caption: 'Probability (%)', type: 'NUMBER', aggregation: 'avg' },
        { column: 'day_open', caption: 'Days to Assign', type: 'NUMBER', aggregation: 'avg', description: 'Average days from creation to assignment' },
        { column: 'day_close', caption: 'Days to Close', type: 'NUMBER', aggregation: 'avg', description: 'Average days from creation to close' },
        {
            column: 'id',
            name: 'leadCount',
            caption: 'Lead Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
