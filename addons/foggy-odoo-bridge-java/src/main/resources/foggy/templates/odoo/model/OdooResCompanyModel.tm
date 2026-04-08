/**
 * Odoo Res Company Model (res.company)
 *
 * @description Company master data — typically used as a dimension table for multi-company filtering
 */
export const model = {
    name: 'OdooResCompanyModel',
    caption: 'Companies',
    tableName: 'res_company',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'currency',
            tableName: 'res_currency',
            foreignKey: 'currency_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Currency',
            description: 'Company default currency'
        },
        {
            name: 'parent',
            tableName: 'res_company',
            foreignKey: 'parent_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Parent Company',
            description: 'Parent company in multi-company hierarchy',
            closureTableName: 'res_company_closure',
            parentKey: 'parent_id',
            childKey: 'company_id'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'name', caption: 'Company Name', type: 'STRING' },
        { column: 'email', caption: 'Email', type: 'STRING' },
        { column: 'phone', caption: 'Phone', type: 'STRING' },
        { column: 'active', caption: 'Active', type: 'BOOL' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' }
    ],

    measures: [
        {
            column: 'id',
            name: 'companyCount',
            caption: 'Company Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
