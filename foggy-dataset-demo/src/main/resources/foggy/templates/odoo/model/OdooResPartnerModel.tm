/**
 * Odoo Res Partner Model (res.partner)
 *
 * @description Partners (customers, vendors, contacts) with company and country dimensions.
 *              Use customer_rank > 0 for customers, supplier_rank > 0 for vendors.
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'OdooResPartnerModel',
    caption: 'Partners',
    tableName: 'res_partner',
    idColumn: 'id',

    dimensions: [
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
            captionColumn: 'name',
            caption: 'Country',
            description: 'Partner country',
            properties: [
                { column: 'code', caption: 'Country Code', type: 'STRING' }
            ]
        },
        {
            name: 'state',
            tableName: 'res_country_state',
            foreignKey: 'state_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'State',
            description: 'Partner state/province',
            properties: [
                { column: 'code', caption: 'State Code', type: 'STRING' }
            ]
        },
        {
            name: 'parentPartner',
            tableName: 'res_partner',
            foreignKey: 'parent_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Parent Company',
            description: 'Parent company for contacts',
            closureTableName: 'res_partner_closure',
            parentKey: 'parent_id',
            childKey: 'partner_id'
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
            captionColumn: 'name',
            caption: 'Sales Team',
            description: 'Sales team'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'name', caption: 'Name', type: 'STRING' },
        { column: 'complete_name', caption: 'Full Name', type: 'STRING' },
        { column: 'type', caption: 'Address Type', type: 'STRING', dictRef: dicts.partner_type },
        { column: 'email', caption: 'Email', type: 'STRING' },
        { column: 'phone', caption: 'Phone', type: 'STRING' },
        { column: 'mobile', caption: 'Mobile', type: 'STRING' },
        { column: 'street', caption: 'Street', type: 'STRING' },
        { column: 'city', caption: 'City', type: 'STRING' },
        { column: 'zip', caption: 'ZIP', type: 'STRING' },
        { column: 'website', caption: 'Website', type: 'STRING' },
        { column: 'vat', caption: 'Tax ID', type: 'STRING' },
        { column: 'ref', caption: 'Internal Reference', type: 'STRING' },
        { column: 'lang', caption: 'Language', type: 'STRING' },
        { column: 'is_company', caption: 'Is Company', type: 'BOOL' },
        { column: 'active', caption: 'Active', type: 'BOOL' },
        { column: 'customer_rank', caption: 'Customer Rank', type: 'INTEGER', description: '> 0 means customer' },
        { column: 'supplier_rank', caption: 'Supplier Rank', type: 'INTEGER', description: '> 0 means vendor' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        {
            column: 'id',
            name: 'partnerCount',
            caption: 'Partner Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
