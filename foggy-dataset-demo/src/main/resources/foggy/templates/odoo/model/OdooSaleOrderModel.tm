/**
 * Odoo Sale Order Model (sale.order)
 *
 * @description Sales order header table with customer, salesperson, team and company dimensions
 */
import { dicts } from '../dicts.fsscript';
import { jsonbCaption } from '../odoo17.fsscript';

export const model = {
    name: 'OdooSaleOrderModel',
    caption: 'Sale Orders',
    tableName: 'sale_order',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'dateOrder',
            foreignKey: 'date_order',
            primaryKey: 'date_order',
            captionColumn: 'date_order',
            caption: 'Order Date',
            description: 'Self date dimension backed by sale_order.date_order without joining dim_date',
            type: 'DATETIME',
            timeRole: 'business_date',
            recommendedUse: 'Primary sales order business date for timeWindow, trend, and period pivot queries.',
            properties: [
                {
                    column: 'date_order',
                    name: 'year',
                    caption: 'Order Year',
                    type: 'INTEGER',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `CAST(strftime('%Y', ${alias}.date_order) AS INTEGER)`; } },
                        postgresql: { builder: (alias) => { return `EXTRACT(YEAR FROM ${alias}.date_order)`; } },
                        mysql: { builder: (alias) => { return `YEAR(${alias}.date_order)`; } },
                        sqlserver: { builder: (alias) => { return `DATEPART(year, ${alias}.date_order)`; } }
                    }
                },
                {
                    column: 'date_order',
                    name: 'month',
                    caption: 'Order Month',
                    type: 'INTEGER',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `CAST(strftime('%m', ${alias}.date_order) AS INTEGER)`; } },
                        postgresql: { builder: (alias) => { return `EXTRACT(MONTH FROM ${alias}.date_order)`; } },
                        mysql: { builder: (alias) => { return `MONTH(${alias}.date_order)`; } },
                        sqlserver: { builder: (alias) => { return `DATEPART(month, ${alias}.date_order)`; } }
                    }
                },
                {
                    column: 'date_order',
                    name: 'yearMonth',
                    caption: 'Order Year-Month',
                    type: 'STRING',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `strftime('%Y-%m', ${alias}.date_order)`; } },
                        postgresql: { builder: (alias) => { return `TO_CHAR(${alias}.date_order, 'YYYY-MM')`; } },
                        mysql: { builder: (alias) => { return `DATE_FORMAT(${alias}.date_order, '%Y-%m')`; } },
                        sqlserver: { builder: (alias) => { return `CONVERT(char(7), ${alias}.date_order, 120)`; } }
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
            name: 'partnerCountry',
            tableName: 'res_country',
            foreignKey: 'country_id',
            primaryKey: 'id',
            joinTo: 'partner',
            captionDef: jsonbCaption(),
            caption: 'Customer Country',
            description: 'Country of the sale order customer',
            properties: [
                { column: 'code', caption: 'Country Code', type: 'STRING' }
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
            captionDef: jsonbCaption(),
            caption: 'Sales Team',
            description: 'Sales team responsible'
        },
        {
            name: 'pricelist',
            tableName: 'product_pricelist',
            foreignKey: 'pricelist_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
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
        { column: 'commitment_date', caption: 'Delivery Date', type: 'DATETIME', description: 'Expected delivery date',
          timeRole: 'planned_delivery_date', recommendedUse: 'Use for promised delivery or commitment-date analysis, not for sales booking period.' },
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
