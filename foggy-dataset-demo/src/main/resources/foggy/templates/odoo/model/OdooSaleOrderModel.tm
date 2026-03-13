/**
 * Odoo Sale Order Model (sale.order)
 *
 * @description Sales order header table with customer, salesperson, team and company dimensions
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'OdooSaleOrderModel',
    caption: 'Sale Orders',
    tableName: 'sale_order',
    idColumn: 'id',

    dimensions: [
        {
            name: 'partner',
            tableName: 'res_partner',
            foreignKey: 'partner_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Customer',
            description: 'Customer or shipping address',
            properties: [
                { column: 'email', caption: 'Email', type: 'STRING' },
                { column: 'phone', caption: 'Phone', type: 'STRING' },
                { column: 'city', caption: 'City', type: 'STRING' },
                { column: 'country_id', caption: 'Country ID', type: 'INTEGER' },
                { column: 'is_company', caption: 'Is Company', type: 'BOOL' },
                { column: 'customer_rank', caption: 'Customer Rank', type: 'INTEGER' }
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
            name: 'salesTeam',
            tableName: 'crm_team',
            foreignKey: 'team_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Sales Team',
            description: 'Sales team responsible'
        },
        {
            name: 'pricelist',
            tableName: 'product_pricelist',
            foreignKey: 'pricelist_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Pricelist',
            description: 'Applied pricelist'
        },
        {
            name: 'warehouse',
            tableName: 'stock_warehouse',
            foreignKey: 'warehouse_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Warehouse',
            description: 'Delivery warehouse'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'name', caption: 'Order Reference', type: 'STRING', description: 'Order number (e.g. S00001)' },
        { column: 'state', caption: 'Status', type: 'STRING', dictRef: dicts.sale_order_state },
        { column: 'date_order', caption: 'Order Date', type: 'DATETIME', description: 'Quotation/order date' },
        { column: 'commitment_date', caption: 'Delivery Date', type: 'DATETIME', description: 'Expected delivery date' },
        { column: 'invoice_status', caption: 'Invoice Status', type: 'STRING', dictRef: dicts.sale_invoice_status },
        { column: 'client_order_ref', caption: 'Customer Reference', type: 'STRING' },
        { column: 'origin', caption: 'Source Document', type: 'STRING' },
        { column: 'note', caption: 'Terms & Conditions', type: 'STRING' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'amount_untaxed', caption: 'Untaxed Amount', type: 'MONEY', aggregation: 'sum' },
        { column: 'amount_tax', caption: 'Taxes', type: 'MONEY', aggregation: 'sum' },
        { column: 'amount_total', caption: 'Total', type: 'MONEY', aggregation: 'sum' },
        { column: 'currency_rate', caption: 'Currency Rate', type: 'NUMBER' },
        {
            column: 'id',
            name: 'orderCount',
            caption: 'Order Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
