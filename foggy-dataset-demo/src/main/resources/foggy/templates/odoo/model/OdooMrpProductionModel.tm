/**
 * Odoo Manufacturing Production Model (mrp.production)
 *
 * @description Manufacturing orders with product, BOM, routing, and company dimensions.
 *              Requires 'mrp' Odoo module to be installed.
 */
import { dicts } from '../dicts.fsscript';
import { jsonbCaption } from '../odoo17.fsscript';

export const model = {
    name: 'OdooMrpProductionModel',
    caption: 'Manufacturing Orders',
    tableName: 'mrp_production',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'product',
            tableName: 'product_product',
            foreignKey: 'product_id',
            primaryKey: 'id',
            captionColumn: 'default_code',
            caption: 'Product',
            description: 'Product to manufacture',
            properties: [
                { column: 'active', caption: 'Active', type: 'BOOL' },
                { column: 'barcode', caption: 'Barcode', type: 'STRING' }
            ]
        },
        // NOTE: product_tmpl_id is a computed (non-stored) field in Odoo 17.
        // To get product template info, join product_product → product_template via product_tmpl_id on product_product.
        {
            name: 'bom',
            tableName: 'mrp_bom',
            foreignKey: 'bom_id',
            primaryKey: 'id',
            captionColumn: 'code',
            caption: 'Bill of Materials',
            description: 'BOM used for this manufacturing order',
            properties: [
                { column: 'type', caption: 'BOM Type', type: 'STRING' },
                { column: 'product_qty', caption: 'BOM Quantity', type: 'NUMBER' }
            ]
        },
        {
            name: 'productUom',
            tableName: 'uom_uom',
            foreignKey: 'product_uom_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Unit of Measure',
            description: 'Unit of measure for the product'
        },
        {
            name: 'responsible',
            tableName: 'res_users',
            foreignKey: 'user_id',
            primaryKey: 'id',
            captionColumn: 'login',
            caption: 'Responsible',
            description: 'Responsible user'
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
            name: 'pickingType',
            tableName: 'stock_picking_type',
            foreignKey: 'picking_type_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Operation Type',
            description: 'Stock operation type (e.g. Manufacturing)'
        },
        {
            name: 'location',
            tableName: 'stock_location',
            foreignKey: 'location_src_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Source Location',
            description: 'Source location for raw materials'
        },
        {
            name: 'locationDest',
            tableName: 'stock_location',
            foreignKey: 'location_dest_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Destination Location',
            description: 'Destination location for finished products'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'name', caption: 'Reference', type: 'STRING', description: 'Manufacturing order reference (e.g. MO/00001)' },
        { column: 'state', caption: 'Status', type: 'STRING', dictRef: dicts.mrp_production_state },
        { column: 'priority', caption: 'Priority', type: 'STRING',
          description: '0 = Normal, 1 = Urgent' },
        { column: 'origin', caption: 'Source Document', type: 'STRING',
          description: 'Source document (e.g. SO number)' },
        { column: 'date_start', caption: 'Start Date', type: 'DATETIME',
          description: 'Planned start date' },
        { column: 'date_finished', caption: 'End Date', type: 'DATETIME',
          description: 'Actual end date' },
        { column: 'date_deadline', caption: 'Deadline', type: 'DATETIME' },
        { column: 'is_locked', caption: 'Is Locked', type: 'BOOL' },
        { column: 'consumption', caption: 'Consumption', type: 'STRING',
          description: 'flexible, strict, or warning' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'product_qty', caption: 'Quantity to Produce', type: 'NUMBER', aggregation: 'sum' },
        { column: 'qty_producing', caption: 'Quantity Producing', type: 'NUMBER', aggregation: 'sum',
          description: 'Current quantity being produced (qty_produced is computed/non-stored in Odoo 17)' },
        {
            column: 'id',
            name: 'productionCount',
            caption: 'MO Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
