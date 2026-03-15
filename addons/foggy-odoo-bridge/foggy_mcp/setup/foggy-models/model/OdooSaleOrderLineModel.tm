/**
 * Odoo Sale Order Line Model (sale.order.line)
 *
 * @description Sales order line items with product, order, and UoM dimensions
 */
import { dicts } from '../dicts.fsscript';
import { jsonbCaption } from '../odoo17.fsscript';

export const model = {
    name: 'OdooSaleOrderLineModel',
    caption: 'Sale Order Lines',
    tableName: 'sale_order_line',
    idColumn: 'id',

    dimensions: [
        {
            name: 'order',
            tableName: 'sale_order',
            foreignKey: 'order_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Sale Order',
            description: 'Parent sale order',
            properties: [
                { column: 'state', caption: 'Order Status', type: 'STRING', dictRef: dicts.sale_order_state },
                { column: 'date_order', caption: 'Order Date', type: 'DATETIME' }
            ]
        },
        {
            name: 'product',
            tableName: 'product_product',
            foreignKey: 'product_id',
            primaryKey: 'id',
            captionColumn: 'default_code',
            caption: 'Product',
            description: 'Product variant',
            properties: [
                { column: 'active', caption: 'Active', type: 'BOOL' },
                { column: 'barcode', caption: 'Barcode', type: 'STRING' }
            ]
        },
        {
            name: 'uom',
            tableName: 'uom_uom',
            foreignKey: 'product_uom',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Unit of Measure',
            description: 'Unit of measure for the line'
        },
        {
            name: 'salesperson',
            tableName: 'res_users',
            foreignKey: 'salesman_id',
            primaryKey: 'id',
            captionColumn: 'login',
            caption: 'Salesperson',
            description: 'Salesperson on the line'
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
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'name', caption: 'Description', type: 'STRING' },
        { column: 'sequence', caption: 'Sequence', type: 'INTEGER' },
        { column: 'invoice_status', caption: 'Invoice Status', type: 'STRING', dictRef: dicts.sale_invoice_status },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' }
    ],

    measures: [
        { column: 'product_uom_qty', caption: 'Ordered Qty', type: 'NUMBER', aggregation: 'sum' },
        { column: 'qty_delivered', caption: 'Delivered Qty', type: 'NUMBER', aggregation: 'sum' },
        { column: 'qty_invoiced', caption: 'Invoiced Qty', type: 'NUMBER', aggregation: 'sum' },
        { column: 'qty_to_invoice', caption: 'To Invoice Qty', type: 'NUMBER', aggregation: 'sum' },
        { column: 'price_unit', caption: 'Unit Price', type: 'MONEY' },
        { column: 'discount', caption: 'Discount (%)', type: 'NUMBER' },
        { column: 'price_subtotal', caption: 'Subtotal', type: 'MONEY', aggregation: 'sum' },
        { column: 'price_tax', caption: 'Tax Amount', type: 'MONEY', aggregation: 'sum' },
        { column: 'price_total', caption: 'Total', type: 'MONEY', aggregation: 'sum' },
        {
            column: 'id',
            name: 'lineCount',
            caption: 'Line Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
