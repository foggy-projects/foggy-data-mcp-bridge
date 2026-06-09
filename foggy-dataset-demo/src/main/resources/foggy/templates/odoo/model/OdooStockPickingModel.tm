/**
 * Odoo Stock Picking Model (stock.picking)
 *
 * @description Inventory transfers (receipts, deliveries, internal) with partner,
 *              picking type, location, and company dimensions
 */
import { dicts } from '../dicts.fsscript';
import { jsonbCaption } from '../odoo17.fsscript';

export const model = {
    name: 'OdooStockPickingModel',
    caption: 'Inventory Transfers',
    tableName: 'stock_picking',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'scheduledDate',
            foreignKey: 'scheduled_date',
            primaryKey: 'scheduled_date',
            captionColumn: 'scheduled_date',
            caption: 'Scheduled Date',
            description: 'Self date dimension backed by stock_picking.scheduled_date without joining dim_date',
            type: 'DATETIME',
            timeRole: 'business_date',
            recommendedUse: 'Primary inventory transfer business date for scheduled receipts, deliveries, and period pivot queries.',
            properties: [
                {
                    column: 'scheduled_date',
                    name: 'year',
                    caption: 'Scheduled Year',
                    type: 'INTEGER',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `CAST(strftime('%Y', ${alias}.scheduled_date) AS INTEGER)`; } },
                        postgresql: { builder: (alias) => { return `EXTRACT(YEAR FROM ${alias}.scheduled_date)`; } },
                        mysql: { builder: (alias) => { return `YEAR(${alias}.scheduled_date)`; } },
                        sqlserver: { builder: (alias) => { return `DATEPART(year, ${alias}.scheduled_date)`; } }
                    }
                },
                {
                    column: 'scheduled_date',
                    name: 'month',
                    caption: 'Scheduled Month',
                    type: 'INTEGER',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `CAST(strftime('%m', ${alias}.scheduled_date) AS INTEGER)`; } },
                        postgresql: { builder: (alias) => { return `EXTRACT(MONTH FROM ${alias}.scheduled_date)`; } },
                        mysql: { builder: (alias) => { return `MONTH(${alias}.scheduled_date)`; } },
                        sqlserver: { builder: (alias) => { return `DATEPART(month, ${alias}.scheduled_date)`; } }
                    }
                },
                {
                    column: 'scheduled_date',
                    name: 'yearMonth',
                    caption: 'Scheduled Year-Month',
                    type: 'STRING',
                    dialectFormulaDef: {
                        sqlite: { builder: (alias) => { return `strftime('%Y-%m', ${alias}.scheduled_date)`; } },
                        postgresql: { builder: (alias) => { return `TO_CHAR(${alias}.scheduled_date, 'YYYY-MM')`; } },
                        mysql: { builder: (alias) => { return `DATE_FORMAT(${alias}.scheduled_date, '%Y-%m')`; } },
                        sqlserver: { builder: (alias) => { return `CONVERT(char(7), ${alias}.scheduled_date, 120)`; } }
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
            caption: 'Contact',
            description: 'Delivery/receipt address',
            properties: [
                { column: 'email', caption: 'Email', type: 'STRING' },
                { column: 'city', caption: 'City', type: 'STRING' }
            ]
        },
        {
            name: 'pickingType',
            tableName: 'stock_picking_type',
            foreignKey: 'picking_type_id',
            primaryKey: 'id',
            // Odoo 17: stock_picking_type.name is JSONB
            captionDef: jsonbCaption(),
            caption: 'Operation Type',
            description: 'Receipt / Delivery / Internal transfer',
            properties: [
                { column: 'code', caption: 'Type Code', type: 'STRING', description: 'incoming / outgoing / internal' },
                { column: 'sequence_code', caption: 'Sequence Prefix', type: 'STRING' }
            ]
        },
        {
            name: 'locationSrc',
            tableName: 'stock_location',
            foreignKey: 'location_id',
            primaryKey: 'id',
            captionColumn: 'complete_name',
            caption: 'Source Location',
            description: 'Source stock location'
        },
        {
            name: 'locationDest',
            tableName: 'stock_location',
            foreignKey: 'location_dest_id',
            primaryKey: 'id',
            captionColumn: 'complete_name',
            caption: 'Destination Location',
            description: 'Destination stock location'
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
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'name', caption: 'Reference', type: 'STRING', description: 'Transfer reference (e.g. WH/IN/00001)' },
        { column: 'state', caption: 'Status', type: 'STRING', dictRef: dicts.stock_picking_state },
        { column: 'origin', caption: 'Source Document', type: 'STRING', description: 'Source document (e.g. SO001)' },
        { column: 'date_deadline', caption: 'Deadline', type: 'DATETIME',
          timeRole: 'deadline_date', recommendedUse: 'Use for deadline and late-transfer analysis.' },
        { column: 'date_done', caption: 'Effective Date', type: 'DATETIME', description: 'Date of transfer completion',
          timeRole: 'completion_date', recommendedUse: 'Use for completed transfer throughput and actual receipt/delivery analysis.' },
        { column: 'priority', caption: 'Priority', type: 'STRING' },
        { column: 'note', caption: 'Notes', type: 'STRING' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        {
            column: 'id',
            name: 'transferCount',
            caption: 'Transfer Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
