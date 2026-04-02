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
        { column: 'date_order', caption: 'Order Deadline', type: 'DATETIME' },
        { column: 'date_approve', caption: 'Confirmation Date', type: 'DATETIME' },
        { column: 'date_planned', caption: 'Expected Arrival', type: 'DATETIME' },
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
