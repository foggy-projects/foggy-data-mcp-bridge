/**
 * Odoo Product Template Model (product.template)
 *
 * @description Product catalog with category, UoM, and company dimensions.
 *              Covers both physical products and services.
 */
import { dicts } from '../dicts.fsscript';
import { jsonbCaption } from '../odoo17.fsscript';

export const model = {
    name: 'OdooProductTemplateModel',
    caption: 'Products',
    tableName: 'product_template',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'category',
            tableName: 'product_category',
            foreignKey: 'categ_id',
            primaryKey: 'id',
            captionColumn: 'complete_name',
            caption: 'Product Category',
            description: 'Product category (hierarchical)'
        },
        {
            name: 'uom',
            tableName: 'uom_uom',
            foreignKey: 'uom_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Unit of Measure',
            description: 'Default unit of measure for sales'
        },
        {
            name: 'purchaseUom',
            tableName: 'uom_uom',
            foreignKey: 'uom_po_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Purchase UoM',
            description: 'Default unit of measure for purchases'
        },
        {
            name: 'company',
            tableName: 'res_company',
            foreignKey: 'company_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Company',
            description: 'Company (empty = shared across all companies)',
            closureTableName: 'res_company_closure',
            parentKey: 'parent_id',
            childKey: 'company_id'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        // Odoo 17: product_template.name is JSONB
        { column: 'name', caption: 'Product Name', type: 'STRING',
          description: 'JSONB translatable field — use jsonbCaption in QM if needed' },
        { column: 'default_code', caption: 'Internal Reference', type: 'STRING',
          description: 'SKU or internal reference code' },
        { column: 'detailed_type', caption: 'Product Type', type: 'STRING', dictRef: dicts.product_detailed_type },
        { column: 'active', caption: 'Active', type: 'BOOL' },
        { column: 'sale_ok', caption: 'Can be Sold', type: 'BOOL' },
        { column: 'purchase_ok', caption: 'Can be Purchased', type: 'BOOL' },
        { column: 'invoice_policy', caption: 'Invoicing Policy', type: 'STRING', dictRef: dicts.product_invoice_policy },
        { column: 'tracking', caption: 'Tracking', type: 'STRING',
          description: 'none / lot / serial' },
        { column: 'priority', caption: 'Favorite', type: 'STRING' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'list_price', caption: 'Sales Price', type: 'MONEY' },
        { column: 'weight', caption: 'Weight (kg)', type: 'NUMBER' },
        { column: 'volume', caption: 'Volume (m\u00b3)', type: 'NUMBER' },
        {
            column: 'id',
            name: 'productCount',
            caption: 'Product Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
