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
            name: 'dateFinished',
            foreignKey: 'date_finished',
            primaryKey: 'date_finished',
            captionColumn: 'date_finished',
            caption: 'Finished Date',
            description: 'Self date dimension backed by mrp_production.date_finished without joining dim_date',
            type: 'DATETIME',
            timeRole: 'business_date',
            recommendedUse: 'Primary manufacturing completion date for production count, throughput, and period pivot queries.',
            properties: [
                {
                    column: 'date_finished',
                    name: 'year',
                    caption: 'Finished Year',
                    type: 'INTEGER',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `CAST(strftime('%Y', ${alias}.date_finished) AS INTEGER)`; } },
                        postgresql: { builder: (alias) => { return `EXTRACT(YEAR FROM ${alias}.date_finished)`; } },
                        mysql: { builder: (alias) => { return `YEAR(${alias}.date_finished)`; } },
                        sqlserver: { builder: (alias) => { return `DATEPART(year, ${alias}.date_finished)`; } }
                    }
                },
                {
                    column: 'date_finished',
                    name: 'month',
                    caption: 'Finished Month',
                    type: 'INTEGER',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `CAST(strftime('%m', ${alias}.date_finished) AS INTEGER)`; } },
                        postgresql: { builder: (alias) => { return `EXTRACT(MONTH FROM ${alias}.date_finished)`; } },
                        mysql: { builder: (alias) => { return `MONTH(${alias}.date_finished)`; } },
                        sqlserver: { builder: (alias) => { return `DATEPART(month, ${alias}.date_finished)`; } }
                    }
                },
                {
                    column: 'date_finished',
                    name: 'yearMonth',
                    caption: 'Finished Year-Month',
                    type: 'STRING',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `strftime('%Y-%m', ${alias}.date_finished)`; } },
                        postgresql: { builder: (alias) => { return `TO_CHAR(${alias}.date_finished, 'YYYY-MM')`; } },
                        mysql: { builder: (alias) => { return `DATE_FORMAT(${alias}.date_finished, '%Y-%m')`; } },
                        sqlserver: { builder: (alias) => { return `CONVERT(char(7), ${alias}.date_finished, 120)`; } }
                    }
                }
            ]
        },
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
        captionColumn: 'complete_name',
        caption: 'Source Location',
            description: 'Source location for raw materials'
        },
        {
            name: 'locationDest',
        tableName: 'stock_location',
        foreignKey: 'location_dest_id',
        primaryKey: 'id',
        captionColumn: 'complete_name',
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
          description: 'Planned start date',
          timeRole: 'start_date', recommendedUse: 'Use for planned manufacturing start and queue analysis.' },
        { column: 'date_deadline', caption: 'Deadline', type: 'DATETIME',
          timeRole: 'deadline_date', recommendedUse: 'Use for manufacturing deadline and lateness analysis.' },
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
