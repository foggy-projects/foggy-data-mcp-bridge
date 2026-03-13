/**
 * Odoo Stock Picking Model (stock.picking)
 *
 * @description Inventory transfers (receipts, deliveries, internal) with partner,
 *              picking type, location, and company dimensions
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'OdooStockPickingModel',
    caption: 'Inventory Transfers',
    tableName: 'stock_picking',
    idColumn: 'id',

    dimensions: [
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
            captionColumn: 'name',
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
        { column: 'scheduled_date', caption: 'Scheduled Date', type: 'DATETIME' },
        { column: 'date_deadline', caption: 'Deadline', type: 'DATETIME' },
        { column: 'date_done', caption: 'Effective Date', type: 'DATETIME', description: 'Date of transfer completion' },
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
