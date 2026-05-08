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
    dataSourceName: 'odoo',
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
                { column: 'date_order', caption: 'Order Date', type: 'DATETIME',
                  timeRole: 'business_date', recommendedUse: 'Primary sales line business date inherited from the parent sale order.' }
            ]
        },
        {
            name: 'orderDate',
            tableName: 'sale_order',
            foreignKey: 'order_id',
            primaryKey: 'id',
            captionColumn: 'date_order',
            caption: 'Order Date',
            description: 'Parent sale order date-grain dimension backed by sale_order.date_order without joining dim_date',
            type: 'DATETIME',
            timeRole: 'business_date',
            recommendedUse: 'Use for sales-line trend and product-by-month period pivot queries.',
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
            name: 'orderPartner',
            tableName: 'res_partner',
            foreignKey: 'partner_id',
            primaryKey: 'id',
            joinTo: 'order',
            captionColumn: 'name',
            caption: 'Order Customer',
            description: 'Customer on the parent sale order',
            properties: [
                { column: 'email', caption: 'Email', type: 'STRING' },
                { column: 'city', caption: 'City', type: 'STRING' },
                { column: 'is_company', caption: 'Is Company', type: 'BOOL' }
            ]
        },
        {
            name: 'orderPartnerCountry',
            tableName: 'res_country',
            foreignKey: 'country_id',
            primaryKey: 'id',
            joinTo: 'orderPartner',
            captionDef: jsonbCaption(),
            caption: 'Order Customer Country',
            description: 'Country of the customer on the parent sale order',
            properties: [
                { column: 'code', caption: 'Country Code', type: 'STRING' }
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
