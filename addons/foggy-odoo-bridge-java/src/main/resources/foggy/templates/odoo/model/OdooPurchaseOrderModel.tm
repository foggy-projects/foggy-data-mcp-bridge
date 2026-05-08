/**
 * Odoo Purchase Order Model (purchase.order)
 *
 * @description Purchase order header table with vendor, buyer, and company dimensions
 */
import { dicts } from '../dicts.fsscript';
import { jsonbCaption } from '../odoo17.fsscript';

export const model = {
    name: 'OdooPurchaseOrderModel',
    caption: 'Purchase Orders',
    tableName: 'purchase_order',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'dateOrder',
            foreignKey: 'date_order',
            primaryKey: 'date_order',
            captionColumn: 'date_order',
            caption: 'Order Date',
            description: 'Self date dimension backed by purchase_order.date_order without joining dim_date',
            type: 'DATETIME',
            timeRole: 'business_date',
            recommendedUse: 'Primary purchase order business date for procurement trend and period pivot queries.',
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
            name: 'vendor',
            tableName: 'res_partner',
            foreignKey: 'partner_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Vendor',
            description: 'Supplier or vendor',
            properties: [
                { column: 'email', caption: 'Email', type: 'STRING' },
                { column: 'phone', caption: 'Phone', type: 'STRING' },
                { column: 'city', caption: 'City', type: 'STRING' },
                { column: 'country_id', caption: 'Country ID', type: 'INTEGER' },
                { column: 'supplier_rank', caption: 'Supplier Rank', type: 'INTEGER' }
            ]
        },
        {
            name: 'buyer',
            tableName: 'res_users',
            foreignKey: 'user_id',
            primaryKey: 'id',
            captionColumn: 'login',
            caption: 'Purchase Representative',
            description: 'Responsible buyer'
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
            description: 'Order currency'
        },
        {
            name: 'pickingType',
            tableName: 'stock_picking_type',
            foreignKey: 'picking_type_id',
            primaryKey: 'id',
            // Odoo 17: stock_picking_type.name is JSONB
            captionDef: jsonbCaption(),
            caption: 'Deliver To',
            description: 'Picking type for receipt'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'name', caption: 'Order Reference', type: 'STRING', description: 'PO number (e.g. P00001)' },
        { column: 'state', caption: 'Status', type: 'STRING', dictRef: dicts.purchase_order_state },
        { column: 'date_approve', caption: 'Confirmation Date', type: 'DATETIME',
          timeRole: 'approval_date', recommendedUse: 'Use for purchase order confirmation-cycle analysis.' },
        { column: 'date_planned', caption: 'Expected Arrival', type: 'DATETIME',
          timeRole: 'planned_receipt_date', recommendedUse: 'Use for expected receipt, lateness, and inventory inbound planning analysis.' },
        { column: 'invoice_status', caption: 'Billing Status', type: 'STRING', dictRef: dicts.purchase_invoice_status },
        { column: 'origin', caption: 'Source Document', type: 'STRING' },
        { column: 'notes', caption: 'Terms & Conditions', type: 'STRING' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'amount_untaxed', caption: 'Untaxed Amount', type: 'MONEY', aggregation: 'sum' },
        { column: 'amount_tax', caption: 'Taxes', type: 'MONEY', aggregation: 'sum' },
        { column: 'amount_total', caption: 'Total', type: 'MONEY', aggregation: 'sum' },
        {
            column: 'id',
            name: 'orderCount',
            caption: 'Order Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
